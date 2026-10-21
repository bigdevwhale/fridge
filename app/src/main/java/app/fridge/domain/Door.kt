package app.fridge.domain

import app.fridge.data.Item
import app.fridge.data.ItemDao
import app.fridge.data.Kind
import app.fridge.data.SettingsStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** The weather magnets: how the day is going, said without words. */
enum class Weather { SUN, PARTLY, WIND, FOG, RAIN, STORM }

/** The wordless request magnets. */
enum class Ask { HUG, QUIET, TALK, TEA, WALK, GENTLE }

/**
 * Every change to the door goes through here: it stamps the edit with the next Lamport version and
 * this phone's id, writes it, and hands it to [onChange] — the hum, which passes it to the other
 * phone if it's here.
 */
class Door(
    private val dao: ItemDao,
    private val settings: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var onChange: (List<Item>) -> Unit = {}

    suspend fun me(): String = settings.deviceId()

    fun today(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    suspend fun new(kind: Kind, text: String = "", day: Long = 0, blob: ByteArray? = null): Item {
        val now = clock()
        return Item(id = UUID.randomUUID().toString(), kind = kind.name, author = me(), text = text, day = day, blob = blob, createdAt = now, changedAt = now)
    }

    suspend fun save(vararg items: Item): List<Item> {
        val version = dao.clock() + 1
        val me = me()
        val now = clock()
        val stamped = items.map { it.copy(version = version, editor = me, changedAt = now, fresh = false) }
        dao.put(stamped)
        onChange(stamped)
        return stamped
    }

    suspend fun edit(id: String, change: (Item) -> Item): Item? {
        val item = dao.get(id) ?: return null
        return save(change(item)).first()
    }

    /** Off the door and into the fridge (the archive). */
    suspend fun archive(id: String) = edit(id) { it.copy(archived = true) }

    /** Gone for good, on both phones: what's left is a tombstone that tells the other phone so. */
    suspend fun remove(id: String) = edit(id) { it.copy(deleted = true, text = "", extra = "", blob = null) }

    suspend fun seen(id: String) = dao.seen(id)

    // --- the shopping pad ---

    suspend fun addGrocery(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val current = dao.current().filter { it.type == Kind.GROCERY }
        // Already on the list (or crossed out earlier): bring that line back rather than writing it twice.
        val same = current.firstOrNull { it.text.equals(t, ignoreCase = true) }
        if (same != null) {
            if (same.done || same.archived) save(same.copy(done = false, doneBy = "", archived = false))
            return
        }
        save(new(Kind.GROCERY, t))
    }

    suspend fun cross(id: String, done: Boolean) {
        val me = me()
        edit(id) { it.copy(done = done, doneBy = if (done) me else "") }
    }

    /** A staple (milk, bread) stays in the jar row when crossed out and comes back with one tap. */
    suspend fun staple(id: String, on: Boolean) = edit(id) { it.copy(state = if (on) 1 else 0) }

    /** Clears the crossed-out lines off the pad. Staples go back to their jars; the rest go away. */
    suspend fun clearCrossed() {
        val crossed = dao.current().filter { it.type == Kind.GROCERY && it.done && !it.archived }
        if (crossed.isEmpty()) return
        save(*crossed.map { if (it.state == 1) it.copy(archived = true) else it.copy(deleted = true) }.toTypedArray())
    }

    // --- weather and asks ---

    suspend fun weather(w: Weather) {
        val me = me()
        val day = today()
        val id = "w-$me-$day"
        val item = dao.get(id) ?: new(Kind.WEATHER, day = day).copy(id = id)
        save(item.copy(text = w.name, deleted = false))
    }

    suspend fun ask(a: Ask) {
        val me = me()
        val already = dao.current().any { it.type == Kind.ASK && it.author == me && it.text == a.name && !it.archived }
        if (!already) save(new(Kind.ASK, a.name))
    }

    /** The other one answered: the magnet comes off the door. */
    suspend fun answer(id: String) = edit(id) { it.copy(state = 1, archived = true) }

    // --- notes ---

    suspend fun note(text: String, forMorning: Boolean) {
        val n = new(Kind.NOTE, text.trim())
        save(if (forMorning) n.copy(showAt = nextMorning(clock())) else n)
    }

    suspend fun heart(id: String, on: Boolean) = edit(id) { it.copy(state = if (on) 1 else 0) }

    /** One worry a day each: the face-down note is for real things, not for every small one. */
    suspend fun canHangWorry(): Boolean {
        val me = me()
        val today = today()
        return dao.current().none { it.type == Kind.WORRY && it.author == me && it.date.toEpochDay() == today }
    }

    suspend fun worry(text: String): Boolean {
        if (!canHangWorry()) return false
        save(new(Kind.WORRY, text.trim()))
        return true
    }

    /** "I'm ready to hear it": the note turns over. */
    suspend fun turnOver(id: String) = edit(id) { it.copy(state = 1, doneBy = "") }

    suspend fun envelope(text: String) = save(new(Kind.ENVELOPE, text.trim()))

    /** Both hands were on the seal at once. */
    suspend fun openEnvelope(id: String) = edit(id) { if (it.state == 1) it else it.copy(state = 1, day = today()) }

    suspend fun drawing(strokes: String) = save(new(Kind.DRAWING).copy(extra = strokes))

    suspend fun thanks(text: String) = save(new(Kind.THANKS, text.trim()))

    suspend fun trip(place: String, day: Long, photo: ByteArray?) = save(new(Kind.TRIP, place.trim(), day = day, blob = photo))

    suspend fun wish(text: String) = save(new(Kind.WISH, text.trim()))

    suspend fun gotIt(id: String, got: Boolean) {
        val me = me()
        edit(id) { it.copy(done = got, doneBy = if (got) me else "") }
    }

    // --- chores ---

    suspend fun chore(text: String, firstTurn: String) = save(new(Kind.CHORE, text.trim()).copy(doneBy = firstTurn))

    /** Done: a line in the month's book for whoever did it, and the turn passes to the other one. */
    suspend fun choreDone(id: String, partner: String) {
        val me = me()
        val chore = dao.get(id) ?: return
        val log = new(Kind.CHORE_LOG, chore.text, day = today()).copy(extra = chore.id)
        save(chore.copy(doneBy = Chores.nextTurn(me, me, partner), state = 0), log)
    }

    /** The one on turn asks to swap; the other answers. */
    suspend fun askSwap(id: String, on: Boolean) = edit(id) { it.copy(state = if (on) 1 else 0) }

    suspend fun swap(id: String, accept: Boolean, partner: String) {
        val me = me()
        edit(id) { c -> if (accept) c.copy(doneBy = if (c.doneBy == me) partner else me, state = 0) else c.copy(state = 0) }
    }

    companion object {
        /** Six in the morning after [now] — or this morning's six, if it's still before it. */
        fun nextMorning(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
            val t = java.time.Instant.ofEpochMilli(now).atZone(zone)
            val six = t.toLocalDate().atTime(LocalTime.of(6, 0)).atZone(zone)
            return (if (t.isBefore(six)) six else six.plusDays(1)).toInstant().toEpochMilli()
        }
    }
}

object Chores {
    /** After [doer] does a chore, it's the other one's turn. */
    fun nextTurn(doer: String, me: String, partner: String): String = if (doer == me) partner.ifEmpty { me } else me

    /** How many chores each did in [month] (epoch days of its first and last day). */
    fun balance(logs: List<Item>, from: Long, to: Long): Map<String, Int> =
        logs.filter { it.type == Kind.CHORE_LOG && it.day in from..to }.groupingBy { it.author }.eachCount()
}
