package app.fridge.sync

import android.util.Log
import app.fridge.data.FridgeSettings
import app.fridge.data.Item
import app.fridge.data.ItemDao
import app.fridge.data.SettingsStore
import app.fridge.data.Stamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.spec.SecretKeySpec

/** How the fridge feels about the other phone right now. */
data class Near(
    /** This phone is listening for the other one. */
    val humming: Boolean = false,
    /** The two phones are talking: everything put on the door shows up on the other at once. */
    val together: Boolean = false,
    /** Envelopes whose seal the other one is holding this very moment. */
    val theirHolds: Set<String> = emptySet(),
)

/** Finds the other phone on the network. The real one is [LanFinder]; tests connect by hand. */
interface Finder {
    fun start(tag: String, me: String, port: Int, found: (InetAddress, Int) -> Unit)
    fun stop()
}

/**
 * The fridge's hum: while it runs, this phone listens on the home network for the other phone and
 * looks for it. When they find each other they open one sealed conversation, swap whatever the
 * other is missing, and keep talking — every change on one door appears on the other at once —
 * until one of them leaves.
 *
 * It runs while someone has a reason for it ([start]/[stop]): the app being open, the background
 * service, the pairing screen. With no secret (never paired) it stays quiet whatever the reasons.
 */
class Hum(
    private val settings: SettingsStore,
    private val dao: ItemDao,
    private val scope: CoroutineScope,
    private val finder: Finder?,
) {
    private val _near = MutableStateFlow(Near())
    val near: StateFlow<Near> = _near.asStateFlow()

    /** Called with what arrived from the other phone and was new here. */
    var onNews: (List<Item>) -> Unit = {}

    /** Called each time the two phones find each other: wherever this is, it's home. */
    var onMet: () -> Unit = {}

    private val reasons = mutableSetOf<String>()
    private var running: Job? = null
    private var runningSecret: ByteArray? = null
    private var server: ServerSocket? = null
    @Volatile private var current: Session? = null
    private val connecting = AtomicBoolean(false)
    private val lastTry = ConcurrentHashMap<String, Long>()
    private val myHolds = mutableSetOf<String>()

    /** The port this phone listens on while humming; 0 when it isn't. For tests. */
    val port: Int get() = server?.localPort ?: 0

    @Synchronized
    fun start(reason: String) {
        reasons += reason
        refresh()
    }

    @Synchronized
    fun stop(reason: String) {
        reasons -= reason
        refresh()
    }

    /** Re-reads the settings: after pairing, unpairing, or a new QR. */
    @Synchronized
    fun refresh() {
        val s = runBlocking { settings.now() }
        val want = reasons.isNotEmpty() && s.secret != null
        if (want && running != null && runningSecret.contentEquals(s.secret)) return
        shutdown()
        if (want) launch(s)
    }

    private fun launch(s: FridgeSettings) {
        val secret = s.secret ?: return
        runningSecret = secret
        val key = Seal.key(secret)
        _near.update { it.copy(humming = true) }
        running = scope.launch(Dispatchers.IO) {
            val me = settings.deviceId()
            val ss = runCatching { ServerSocket(0) }.getOrNull() ?: return@launch
            synchronized(this@Hum) {
                if (!isActive) return@launch ss.close()
                server = ss
            }
            finder?.start(Seal.tag(secret), me, ss.localPort) { host, port -> reach(host, port, key) }
            while (isActive && !ss.isClosed) {
                val socket = runCatching { ss.accept() }.getOrNull() ?: break
                launch { converse(socket, outgoing = false, key) }
            }
        }
    }

    private fun shutdown() {
        finder?.stop()
        running?.cancel()
        running = null
        runningSecret = null
        runCatching { server?.close() }
        server = null
        current?.close()
        current = null
        // A fresh start may call anyone at once.
        lastTry.clear()
        _near.value = Near()
    }

    /** Someone answered the call at [host]:[port]. Talk to them, unless already talking. */
    fun reach(host: InetAddress, port: Int, sealKey: SecretKeySpec? = null) {
        val key = sealKey ?: Seal.key(runningSecret ?: return)
        if (current?.alive == true) return
        val where = "${host.hostAddress}:$port"
        val now = System.currentTimeMillis()
        if (now - (lastTry[where] ?: 0) < RETRY_MS) return
        if (!connecting.compareAndSet(false, true)) return
        lastTry[where] = now
        scope.launch(Dispatchers.IO) {
            val socket = Socket()
            val ok = runCatching { socket.connect(InetSocketAddress(host, port), CONNECT_MS) }.isSuccess
            if (!ok) {
                connecting.set(false)
                runCatching { socket.close() }
                return@launch
            }
            converse(socket, outgoing = true, key)
        }
    }

    /** Sends edits made here to the other phone, if it's here. */
    fun push(items: List<Item>) {
        val s = current ?: return
        if (items.isEmpty()) return
        scope.launch(Dispatchers.IO) { runCatching { s.sendItems(items) }.onFailure { s.close() } }
    }

    /** This hand is on (or off) an envelope's seal. */
    fun hold(id: String, on: Boolean) {
        synchronized(myHolds) { if (on) myHolds += id else myHolds -= id }
        val s = current ?: return
        scope.launch(Dispatchers.IO) { runCatching { s.send(JSONObject().put("t", "hold").put("id", id).put("on", on)) } }
    }

    private suspend fun converse(socket: Socket, outgoing: Boolean, key: SecretKeySpec) {
        val session = Session(socket, key)
        try {
            val s = settings.now()
            val me = settings.deviceId()
            socket.soTimeout = HANDSHAKE_MS
            session.handshake()
            session.send(
                JSONObject().put("t", "hello").put("app", PROTOCOL).put("dev", me).put("name", s.myName)
                    .put("inv", inventory(dao.stamps())),
            )
            val hello = session.receive()
            val peer = hello.optString("dev")
            if (hello.optString("t") != "hello" || peer.isEmpty() || peer == me) return
            if (s.partnerId.isNotEmpty() && peer != s.partnerId) return
            if (s.partnerId.isEmpty()) {
                settings.pair(s.secret ?: return, peer, hello.optString("name"))
            } else if (hello.optString("name").isNotBlank() && hello.optString("name") != s.partnerName) {
                settings.setPartnerName(hello.optString("name"))
            }
            session.peer = peer
            session.initiator = if (outgoing) me else peer
            if (!settle(session, me, peer)) return
            if (outgoing) connecting.set(false)
            socket.soTimeout = IDLE_MS
            _near.update { it.copy(together = true) }
            settings.met(System.currentTimeMillis())
            onMet()

            val theirs = parseInventory(hello.optJSONObject("inv"))
            session.sendItems(Merge.missing(dao.everything(), theirs))
            synchronized(myHolds) { myHolds.toList() }.forEach { session.send(JSONObject().put("t", "hold").put("id", it).put("on", true)) }

            val pinger = scope.launch(Dispatchers.IO) {
                while (isActive && session.alive) {
                    delay(PING_MS)
                    runCatching { session.send(JSONObject().put("t", "ping")) }.onFailure { session.close() }
                }
            }
            try {
                while (session.alive) {
                    val m = session.receive()
                    when (m.optString("t")) {
                        "items" -> {
                            val news = Merge.take(dao, Wire.items(m.getJSONArray("items")), me)
                            if (news.isNotEmpty()) onNews(news)
                        }
                        "hold" -> {
                            val id = m.getString("id")
                            _near.update { n -> n.copy(theirHolds = if (m.optBoolean("on")) n.theirHolds + id else n.theirHolds - id) }
                        }
                        "bye" -> break
                    }
                }
            } finally {
                pinger.cancel()
            }
        } catch (e: Exception) {
            Log.d(TAG, "conversation ended: ${e.message}")
        } finally {
            if (outgoing) connecting.set(false)
            session.close()
            synchronized(this) {
                if (current === session) {
                    current = null
                    _near.update { it.copy(together = false, theirHolds = emptySet()) }
                }
            }
        }
    }

    /**
     * Both phones may call each other at the same moment and end up with two conversations. Each
     * keeps the one started by the smaller device id — the same choice on both sides.
     */
    @Synchronized
    private fun settle(session: Session, me: String, peer: String): Boolean {
        val old = current
        if (old != null && old.alive) {
            // Called the same way twice: the old line has gone quiet (Wi-Fi dropped), the new one is real.
            val keepNew = old.initiator == session.initiator || session.initiator == minOf(me, peer)
            if (!keepNew) return false
            old.close()
        }
        current = session
        return true
    }

    private fun inventory(stamps: List<Stamp>): JSONObject = JSONObject().apply {
        stamps.forEach { put(it.id, "${it.version}:${it.editor}") }
    }

    private fun parseInventory(o: JSONObject?): Map<String, Stamp> {
        if (o == null) return emptyMap()
        return o.keys().asSequence().associateWith { id ->
            val (v, e) = o.getString(id).split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            Stamp(id, v.toLongOrNull() ?: 0, e)
        }
    }

    /**
     * One sealed conversation. Each side opens with a random nonce; every frame after that is sealed
     * with both nonces and its own number as associated data, so frames can't be replayed from
     * another conversation or shuffled within this one.
     */
    private class Session(val socket: Socket, val key: SecretKeySpec) {
        private val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        private val inp = DataInputStream(BufferedInputStream(socket.getInputStream()))
        private val mine = Seal.nonce()
        private val theirs = ByteArray(16)
        private var sent = 0L
        private var got = 0L
        var peer = ""
        var initiator = ""
        @Volatile var alive = true
            private set

        fun handshake() {
            out.write(MAGIC)
            out.write(mine)
            out.flush()
            val magic = ByteArray(MAGIC.size)
            inp.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "not a fridge" }
            inp.readFully(theirs)
        }

        @Synchronized
        fun send(m: JSONObject) {
            val sealed = Seal.close(key, m.toString().toByteArray(), aad(mine, theirs, sent))
            out.writeInt(sealed.size)
            out.write(sealed)
            out.flush()
            sent++
        }

        /** Items go in batches, so a door full of photos doesn't become one giant frame. */
        fun sendItems(items: List<Item>) {
            var batch = mutableListOf<Item>()
            var size = 0
            for (i in items) {
                batch += i
                size += 300 + (i.blob?.size ?: 0) * 4 / 3 + i.text.length + i.extra.length
                if (size > BATCH_BYTES) {
                    send(JSONObject().put("t", "items").put("items", Wire.items(batch)))
                    batch = mutableListOf()
                    size = 0
                }
            }
            if (batch.isNotEmpty()) send(JSONObject().put("t", "items").put("items", Wire.items(batch)))
        }

        fun receive(): JSONObject {
            val size = inp.readInt()
            require(size in 13..MAX_FRAME) { "bad frame" }
            val sealed = ByteArray(size)
            inp.readFully(sealed)
            val plain = Seal.open(key, sealed, aad(theirs, mine, got)) ?: error("not sealed with our key")
            got++
            return JSONObject(String(plain))
        }

        fun close() {
            alive = false
            runCatching { socket.close() }
        }

        private fun aad(from: ByteArray, to: ByteArray, n: Long) = from + to + ByteBuffer.allocate(8).putLong(n).array()
    }

    companion object {
        private const val TAG = "FridgeHum"
        private const val PROTOCOL = 1
        private val MAGIC = "FRDG1".toByteArray()
        private const val CONNECT_MS = 4_000
        private const val HANDSHAKE_MS = 10_000
        private const val IDLE_MS = 45_000
        private const val PING_MS = 15_000L
        private const val RETRY_MS = 4_000L
        private const val BATCH_BYTES = 512 * 1024
        private const val MAX_FRAME = 32 * 1024 * 1024
    }
}
