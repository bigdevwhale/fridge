package app.fridge.domain

import app.fridge.data.FridgeSettings
import app.fridge.data.Item
import app.fridge.data.Kind
import app.fridge.data.Stamp
import app.fridge.sync.HomeNet
import app.fridge.sync.Merge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DomainTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private fun item(id: String, kind: Kind, author: String, version: Long = 1, editor: String = author, block: (Item) -> Item = { it }) =
        block(Item(id = id, kind = kind.name, author = author, createdAt = 0, changedAt = 0, version = version, editor = editor))

    @Test
    fun `a drawing survives the trip as text`() {
        val lines = listOf(Stroke(2, listOf(0.1f to 0.2f, 0.5f to 0.5f)), Stroke(0, listOf(1f to 1f)))
        val back = Strokes.decode(Strokes.encode(lines))
        assertEquals(2, back.size)
        assertEquals(2, back[0].crayon)
        assertEquals(0.5f, back[0].points[1].first, 0.001f)
        assertEquals(1f, back[1].points[0].second, 0.001f)
        assertTrue(Strokes.decode("garbage|;|x;1,2").isEmpty())
    }

    @Test
    fun `the later edit wins, the editor breaks a tie`() {
        val a = item("x", Kind.NOTE, "a", version = 3, editor = "a")
        val b = item("x", Kind.NOTE, "a", version = 4, editor = "b")
        assertTrue(b.beats(a))
        assertFalse(a.beats(b))
        val c = item("x", Kind.NOTE, "a", version = 4, editor = "c")
        assertTrue(c.beats(b))
    }

    @Test
    fun `only what the other phone lacks or has older is sent`() {
        val mine = listOf(
            item("new", Kind.NOTE, "a", version = 1),
            item("older-there", Kind.NOTE, "a", version = 5),
            item("same", Kind.NOTE, "a", version = 2),
            item("tie", Kind.NOTE, "a", version = 7, editor = "z"),
        )
        val theirs = mapOf(
            "older-there" to Stamp("older-there", 4, "a"),
            "same" to Stamp("same", 2, "a"),
            "tie" to Stamp("tie", 7, "b"),
        )
        assertEquals(listOf("new", "older-there", "tie"), Merge.missing(mine, theirs).map { it.id })
    }

    @Test
    fun `a morning note waits for six`() {
        fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 22, h, m).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDateTime.of(2026, 10, 23, 6, 0).atZone(zone).toInstant().toEpochMilli(), Door.nextMorning(at(22, 30), zone))
        assertEquals(LocalDateTime.of(2026, 10, 22, 6, 0).atZone(zone).toInstant().toEpochMilli(), Door.nextMorning(at(2), zone))
    }

    @Test
    fun `the board hides their morning note and your wish's mark`() {
        val s = FridgeSettings(onboarded = true, deviceId = "me", partnerId = "them", secret = ByteArray(32))
        val items = listOf(
            item("n1", Kind.NOTE, "them") { it.copy(showAt = 2_000) },
            item("n2", Kind.NOTE, "me") { it.copy(showAt = 2_000) },
            item("w1", Kind.WISH, "me") { it.copy(done = true, doneBy = "them") },
            item("w2", Kind.WISH, "them") { it.copy(done = true, doneBy = "me") },
            item("gone", Kind.NOTE, "me") { it.copy(deleted = true) },
        )
        val early = Board.of(items, s, now = 1_000, zone = zone)
        assertEquals(listOf("n2"), early.papers.map { it.id })
        assertFalse(early.wishes.first { it.id == "w1" }.done)
        assertTrue(early.wishes.first { it.id == "w2" }.done)
        val later = Board.of(items, s, now = 3_000, zone = zone)
        assertEquals(setOf("n1", "n2"), later.papers.map { it.id }.toSet())
    }

    @Test
    fun `both phones draw the same person in the same ink`() {
        val mine = Board.of(emptyList(), FridgeSettings(deviceId = "aaa", partnerId = "bbb", secret = ByteArray(32)), 0)
        val theirs = Board.of(emptyList(), FridgeSettings(deviceId = "bbb", partnerId = "aaa", secret = ByteArray(32)), 0)
        assertTrue(mine.isBlue("aaa"))
        assertTrue(theirs.isBlue("aaa"))
        assertFalse(theirs.isBlue("bbb"))
    }

    @Test
    fun `chores pass the turn and count by month`() {
        assertEquals("them", Chores.nextTurn("me", "me", "them"))
        assertEquals("me", Chores.nextTurn("them", "me", "them"))
        assertEquals("me", Chores.nextTurn("me", "me", ""))
        val logs = listOf(
            item("l1", Kind.CHORE_LOG, "me") { it.copy(day = 10) },
            item("l2", Kind.CHORE_LOG, "me") { it.copy(day = 12) },
            item("l3", Kind.CHORE_LOG, "them") { it.copy(day = 11) },
            item("l4", Kind.CHORE_LOG, "them") { it.copy(day = 40) },
        )
        assertEquals(mapOf("me" to 2, "them" to 1), Chores.balance(logs, 1, 31))
    }

    @Test
    fun `a network is known by its own part of the address`() {
        fun ip(vararg b: Int) = b.map { it.toByte() }.toByteArray()
        assertEquals("192.168.31.0", HomeNet.network(ip(192, 168, 31, 24), 24))
        assertEquals("192.168.31.0", HomeNet.network(ip(192, 168, 31, 177), 24))
        assertEquals("10.0.0.0", HomeNet.network(ip(10, 1, 2, 3), 8))
        assertEquals("0.0.0.0", HomeNet.network(ip(10, 1, 2, 3), 0))
    }

    @Test
    fun `staples go back to their jar`() {
        val s = FridgeSettings(deviceId = "me", secret = null)
        val items = listOf(
            item("milk", Kind.GROCERY, "me") { it.copy(state = 1, done = true, archived = true, text = "Milk") },
            item("bread", Kind.GROCERY, "me") { it.copy(text = "Bread") },
            item("cheese", Kind.GROCERY, "me") { it.copy(text = "Cheese", done = true) },
        )
        val b = Board.of(items, s, 0)
        assertEquals(listOf("bread", "cheese"), b.groceries.map { it.id })
        assertEquals(listOf("milk"), b.jars.map { it.id })
    }
}
