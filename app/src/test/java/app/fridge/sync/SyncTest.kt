package app.fridge.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.fridge.data.FridgeDatabase
import app.fridge.data.Kind
import app.fridge.data.SettingsStore
import app.fridge.domain.Door
import app.fridge.domain.Weather
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.UUID

/**
 * Two phones in one test: each with its own door, settings and hum, talking over real sockets on
 * localhost. The finder is left out — the test introduces them by hand, as NSD or the beacon would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val phones = mutableListOf<Phone>()

    inner class Phone(name: String) {
        val settings = SettingsStore(app, "settings-$name-${UUID.randomUUID()}")
        val db = FridgeDatabase.create(app, null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val hum = Hum(settings, db.dao(), scope, null)
        val door = Door(db.dao(), settings).also { it.onChange = hum::push }
        val id: String = runBlocking { settings.deviceId() }

        init {
            runBlocking { settings.finishWelcome(name) }
            phones += this
        }

        fun items() = runBlocking { db.dao().current() }
        fun item(text: String) = items().firstOrNull { it.text == text }
    }

    @After
    fun close() {
        phones.forEach {
            it.hum.stop("test")
            it.scope.cancel()
            it.db.close()
        }
    }

    private fun until(what: String, check: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            if (check()) return
            Thread.sleep(50)
        }
        throw AssertionError("never: $what")
    }

    private fun meet(a: Phone, b: Phone) {
        a.hum.start("test")
        b.hum.start("test")
        until("a listens") { a.hum.port != 0 }
        b.hum.reach(InetAddress.getLoopbackAddress(), a.hum.port)
        until("together") { a.hum.near.value.together && b.hum.near.value.together }
    }

    @Test
    fun `the scanned phone meets the one that showed the code and the doors become one`() {
        val anna = Phone("Anna")
        val ben = Phone("Ben")
        val secret = Seal.newSecret()
        runBlocking {
            anna.settings.offer(secret)
            ben.settings.pair(secret, anna.id, "Anna")
            anna.door.note("Dinner's in the oven", forMorning = false)
            ben.door.addGrocery("Milk")
        }
        meet(anna, ben)

        until("anna has the milk") { anna.item("Milk") != null }
        until("ben has the note") { ben.item("Dinner's in the oven") != null }
        // Anna learned who scanned her code from the conversation itself.
        val s = runBlocking { anna.settings.now() }
        assertEquals(ben.id, s.partnerId)
        assertEquals("Ben", s.partnerName)
        assertTrue(s.paired)
        // What came from the other one is fresh; what was written here isn't.
        assertTrue(anna.item("Milk")!!.fresh)
        assertFalse(anna.item("Dinner's in the oven")!!.fresh)

        // While together, an edit travels at once.
        val milk = anna.item("Milk")!!.id
        runBlocking { anna.door.cross(milk, true) }
        until("ben sees it crossed by anna") { ben.item("Milk")?.let { it.done && it.doneBy == anna.id } == true }

        // A removal travels as a tombstone.
        runBlocking { ben.door.remove(ben.item("Dinner's in the oven")!!.id) }
        until("anna's note is gone") { anna.item("Dinner's in the oven") == null }

        // A hand on an envelope's seal reaches the other phone, and so does letting go.
        anna.hum.hold("envelope", true)
        until("ben feels anna's hold") { "envelope" in ben.hum.near.value.theirHolds }
        anna.hum.hold("envelope", false)
        until("ben feels her let go") { "envelope" !in ben.hum.near.value.theirHolds }
    }

    @Test
    fun `apart, the edits wait and travel at the next meeting`() {
        val anna = Phone("Anna")
        val ben = Phone("Ben")
        val secret = Seal.newSecret()
        runBlocking {
            anna.settings.pair(secret, ben.id, "Ben")
            ben.settings.pair(secret, anna.id, "Anna")
        }
        meet(anna, ben)
        ben.hum.stop("test")
        until("apart") { !anna.hum.near.value.together }

        runBlocking {
            anna.door.weather(Weather.STORM)
            anna.door.chore("Take out the trash", ben.id)
            ben.door.thanks("for the soup")
        }
        Thread.sleep(300)
        assertTrue(ben.items().none { it.type == Kind.WEATHER })

        meet(anna, ben)
        until("ben has the storm and the chore") { ben.items().any { it.type == Kind.WEATHER && it.text == "STORM" } && ben.item("Take out the trash") != null }
        until("anna has the thanks") { anna.item("for the soup") != null }
    }

    @Test
    fun `a phone with another secret is never let in`() {
        val anna = Phone("Anna")
        val stranger = Phone("Eve")
        runBlocking {
            anna.settings.offer(Seal.newSecret())
            stranger.settings.pair(Seal.newSecret(), anna.id, "Anna")
            anna.door.note("private", forMorning = false)
        }
        anna.hum.start("test")
        stranger.hum.start("test")
        until("anna listens") { anna.hum.port != 0 }
        stranger.hum.reach(InetAddress.getLoopbackAddress(), anna.hum.port)
        Thread.sleep(1_500)
        assertFalse(anna.hum.near.value.together)
        assertFalse(stranger.hum.near.value.together)
        assertTrue(stranger.items().isEmpty())
        assertTrue(runBlocking { anna.settings.now() }.waiting)
    }

    @Test
    fun `a parcel opens only on the other phone`() {
        val anna = Phone("Anna")
        val ben = Phone("Ben")
        val eve = Phone("Eve")
        val secret = Seal.newSecret()
        runBlocking {
            anna.settings.pair(secret, ben.id, "Ben")
            ben.settings.pair(secret, anna.id, "Anna")
            eve.settings.pair(Seal.newSecret(), anna.id, "Anna")
            anna.door.envelope("I'm proud of you")
            anna.door.trip("Tbilisi", 20_000, ByteArray(5000) { it.toByte() })
        }
        val parcel = runBlocking { Parcel.pack(anna.db.dao(), anna.settings.now(), anna.id) }

        val opened = runBlocking { Parcel.unpack(parcel, ben.db.dao(), ben.settings) }
        assertTrue(opened is Parcel.Opened.Took)
        assertEquals(2, (opened as Parcel.Opened.Took).news.size)
        assertEquals(5000, ben.item("Tbilisi")!!.blob!!.size)
        assertEquals(Parcel.Opened.OwnParcel, runBlocking { Parcel.unpack(parcel, anna.db.dao(), anna.settings) })
        assertEquals(Parcel.Opened.NotOurs, runBlocking { Parcel.unpack(parcel, eve.db.dao(), eve.settings) })
        assertTrue(eve.items().isEmpty())
        // The same parcel twice changes nothing.
        assertEquals(0, (runBlocking { Parcel.unpack(parcel, ben.db.dao(), ben.settings) } as Parcel.Opened.Took).news.size)
    }

    @Test
    fun `an invite survives the QR`() {
        val secret = Seal.newSecret()
        val back = Invite.decode(Invite(secret, "abc123", "Аня").encode())!!
        assertTrue(back.secret.contentEquals(secret))
        assertEquals("abc123", back.deviceId)
        assertEquals("Аня", back.name)
        assertEquals(null, Invite.decode("https://example.com"))
    }
}
