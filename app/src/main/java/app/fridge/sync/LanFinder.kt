package app.fridge.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * Two ways of calling out on the home network, because routers differ: mDNS (Android's NSD), which
 * many routers pass, and a plain UDP broadcast, which nearly all of them do. Whichever answers first
 * wins; both only ever say the fridge's tag, never a name.
 */
class LanFinder(private val context: Context) : Finder {
    private val nsd by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private val wifi by lazy { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    private var scope: CoroutineScope? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var lock: WifiManager.MulticastLock? = null
    private var beacon: DatagramSocket? = null
    private val resolver = Executors.newSingleThreadExecutor()

    @Synchronized
    override fun start(tag: String, me: String, port: Int, found: (InetAddress, Int) -> Unit) {
        stop()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        lock = runCatching { wifi.createMulticastLock("fridge").apply { setReferenceCounted(false); acquire() } }.getOrNull()
        val myName = "fd$tag${me.take(6)}"
        registerNsd(myName, port)
        discoverNsd(tag, myName, found)
        s.launch { broadcast(tag, me, port, found) }
    }

    @Synchronized
    override fun stop() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        scope?.cancel()
        scope = null
        runCatching { beacon?.close() }
        beacon = null
        runCatching { lock?.release() }
        lock = null
    }

    private fun registerNsd(name: String, port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { Log.d(TAG, "nsd register failed $code") }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        registration = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    private fun discoverNsd(tag: String, myName: String, found: (InetAddress, Int) -> Unit) {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { Log.d(TAG, "nsd discovery failed $code") }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                val name = info.serviceName
                // Ours, but not this phone's own call (NSD may add " (2)" to a name on a clash).
                if (!name.startsWith("fd$tag") || name.startsWith(myName)) return
                resolve(info, found)
            }
        }
        discovery = l
        runCatching { nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    /** NSD resolves one service at a time on older Androids; a single thread keeps them in line. */
    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo, found: (InetAddress, Int) -> Unit) = resolver.execute {
        val done = java.util.concurrent.CountDownLatch(1)
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) = done.countDown()
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses.firstOrNull() else info.host
                    host?.let { found(it, info.port) }
                    done.countDown()
                }
            })
        }.onFailure { done.countDown() }
        done.await(5, java.util.concurrent.TimeUnit.SECONDS)
    }

    /** Calls out often for the first minute, then calmly; and listens for the other phone's call. */
    private suspend fun broadcast(tag: String, me: String, port: Int, found: (InetAddress, Int) -> Unit) {
        val socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(BEACON_PORT))
            }
        }.getOrNull() ?: runCatching { DatagramSocket().apply { broadcast = true } }.getOrNull() ?: return
        beacon = socket
        val call = "FD1 $tag $me $port".toByteArray()
        val s = scope ?: return
        s.launch {
            val buf = ByteArray(256)
            while (isActive && !socket.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                runCatching { socket.receive(p) }.onFailure { return@launch }
                val parts = String(p.data, 0, p.length).split(" ")
                if (parts.size == 4 && parts[0] == "FD1" && parts[1] == tag && parts[2] != me) {
                    parts[3].toIntOrNull()?.let { found(p.address, it) }
                }
            }
        }
        var n = 0
        while (s.isActive && !socket.isClosed) {
            for (to in targets()) runCatching { socket.send(DatagramPacket(call, call.size, to, BEACON_PORT)) }
            delay(if (n++ < 20) 3_000 else 12_000)
        }
    }

    /** The general broadcast address, and each local network's own (some phones only pass those). */
    private fun targets(): List<InetAddress> {
        val list = mutableListOf(InetAddress.getByName("255.255.255.255"))
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNullTo(list) { it.broadcast }
        }
        return list.distinct()
    }

    companion object {
        private const val TAG = "FridgeFinder"
        private const val TYPE = "_fridgedoor._tcp"
        private const val BEACON_PORT = 47811
    }
}
