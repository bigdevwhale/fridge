package app.fridge.domain

import app.fridge.data.FridgeSettings
import app.fridge.data.Item
import app.fridge.data.Kind
import java.time.LocalDate
import java.time.ZoneId

/**
 * The door as this phone sees it at [now]: the live items sorted into their places. The other one's
 * morning note stays out of sight until morning, and the "got it" mark on a wish never reaches the
 * one who wished.
 */
data class Board(
    val me: String,
    val partner: String,
    val myName: String,
    val partnerName: String,
    val paired: Boolean,
    val groceries: List<Item>,
    /** Staples that were crossed out: empty jars, one tap to put them back on the list. */
    val jars: List<Item>,
    val chores: List<Item>,
    val choreLogs: List<Item>,
    val weather: List<Item>,
    val asks: List<Item>,
    val papers: List<Item>,
    val thanks: List<Item>,
    val trips: List<Item>,
    val wishes: List<Item>,
    /** Everything that came off the door, newest first. */
    val archive: List<Item>,
    val today: Long,
) {
    fun weatherOf(person: String, day: Long = today): Weather? =
        weather.firstOrNull { it.author == person && it.day == day }?.let { runCatching { Weather.valueOf(it.text) }.getOrNull() }

    /** The name to sign something with: yours, theirs, or a plain word when no name was given. */
    fun nameOf(person: String, me: String, them: String): String =
        if (person == this.me) myName.ifBlank { me } else partnerName.ifBlank { them }

    /** Blue for the phone with the smaller id, red for the other: the same on both doors. */
    fun isBlue(person: String): Boolean = partner.isEmpty() || person == minOf(me, partner)

    val thisMonthThanks: List<Item> get() {
        val first = LocalDate.ofEpochDay(today).withDayOfMonth(1)
        return thanks.filter { !it.date.isBefore(first) }
    }

    companion object {
        fun of(items: List<Item>, s: FridgeSettings, now: Long, zone: ZoneId = ZoneId.systemDefault()): Board {
            val me = s.deviceId
            val visible = items.filter { !it.deleted && (it.showAt <= now || it.author == me) }
            fun of(k: Kind) = visible.filter { it.type == k }
            val wishes = of(Kind.WISH).filter { !it.archived }.map { if (it.author == me) it.copy(done = false, doneBy = "") else it }
            val groceries = of(Kind.GROCERY)
            return Board(
                me = me,
                partner = s.partnerId,
                myName = s.myName,
                partnerName = s.partnerName,
                paired = s.paired,
                groceries = groceries.filter { !it.archived }.sortedWith(compareBy({ it.done }, { it.createdAt })),
                jars = groceries.filter { it.archived && it.state == 1 }.sortedBy { it.text.lowercase() },
                chores = of(Kind.CHORE).filter { !it.archived },
                choreLogs = of(Kind.CHORE_LOG),
                weather = of(Kind.WEATHER),
                asks = of(Kind.ASK).filter { !it.archived },
                papers = visible.filter { it.type in PAPERS && !it.archived },
                thanks = of(Kind.THANKS).sortedBy { it.createdAt },
                trips = of(Kind.TRIP).filter { !it.archived }.sortedBy { it.day },
                wishes = wishes,
                archive = visible.filter { it.archived && it.type in PAPERS + Kind.ASK }.sortedByDescending { it.changedAt },
                today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay(),
            )
        }

        val PAPERS = setOf(Kind.NOTE, Kind.WORRY, Kind.ENVELOPE, Kind.DRAWING)
    }
}
