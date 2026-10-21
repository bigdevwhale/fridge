package app.fridge.data

import app.fridge.AppContainer
import app.fridge.BuildConfig
import app.fridge.domain.Ask
import app.fridge.domain.Stroke
import app.fridge.domain.Strokes
import app.fridge.domain.Weather
import app.fridge.sync.Seal
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The demo build's door (`-Pdemo`): Anna and Ben, a month into sharing a fridge. Everything a
 * screenshot needs — a full shopping pad, chores with a swap asked, the weather, an ask, notes, a
 * worry face down, a sealed envelope, a drawing, thank-yous, trips, wishes, and a few things inside.
 */
object DemoSeed {
    const val PARTNER = "b0b0b0b0b0b0b0b0"

    suspend fun seedIfEmpty(c: AppContainer, ru: Boolean) {
        if (!BuildConfig.DEMO) return
        if (c.db.dao().everything().isNotEmpty()) return
        val me = c.settings.deviceId()
        c.settings.finishWelcome(if (ru) "Аня" else "Anna")
        c.settings.pair(Seal.newSecret(), PARTNER, if (ru) "Боря" else "Ben")
        c.settings.met(System.currentTimeMillis() - 3 * 3_600_000L)
        c.db.dao().put(items(me, PARTNER, ru, System.currentTimeMillis()))
    }

    fun items(me: String, them: String, ru: Boolean, now: Long): List<Item> {
        val zone = ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var n = 0
        val list = mutableListOf<Item>()
        fun at(daysAgo: Long, hour: Int = 9) = today.minusDays(daysAgo).atTime(hour, 15).atZone(zone).toInstant().toEpochMilli()
        fun add(kind: Kind, author: String, text: String = "", daysAgo: Long = 0, block: (Item) -> Item = { it }): Item {
            n++
            val t = at(daysAgo, 8 + n % 12)
            val item = block(Item(id = "demo-$n", kind = kind.name, author = author, text = text, createdAt = t, changedAt = t, version = n.toLong(), editor = author))
            list += item
            return item
        }
        fun s(en: String, r: String) = if (ru) r else en

        // The shopping pad.
        add(Kind.GROCERY, me, s("Milk", "Молоко")) { it.copy(state = 1) }
        add(Kind.GROCERY, them, s("Avocados", "Авокадо"))
        add(Kind.GROCERY, me, s("Bread", "Хлеб"))
        add(Kind.GROCERY, them, s("Coffee beans", "Кофе в зёрнах"))
        add(Kind.GROCERY, me, s("Tomatoes", "Помидоры"))
        add(Kind.GROCERY, them, s("Cheese", "Сыр")) { it.copy(done = true, doneBy = me) }
        add(Kind.GROCERY, me, s("Eggs", "Яйца")) { it.copy(state = 1, done = true, doneBy = them) }
        add(Kind.GROCERY, me, s("Buckwheat", "Гречка")) { it.copy(state = 1, done = true, archived = true) }
        add(Kind.GROCERY, them, s("Bananas", "Бананы")) { it.copy(state = 1, done = true, archived = true) }
        add(Kind.GROCERY, me, s("Butter", "Масло")) { it.copy(state = 1, done = true, archived = true) }

        // Whose turn.
        val trash = add(Kind.CHORE, me, s("Take out the trash", "Вынести мусор"), 20) { it.copy(doneBy = them) }
        val plants = add(Kind.CHORE, them, s("Water the plants", "Полить цветы"), 20) { it.copy(doneBy = me) }
        val vacuum = add(Kind.CHORE, me, s("Vacuum", "Пропылесосить"), 20) { it.copy(doneBy = me, state = 1) }
        add(Kind.CHORE, them, s("Change the sheets", "Сменить постельное"), 20) { it.copy(doneBy = them) }
        val chores = listOf(trash, plants, vacuum)
        for (d in 0 until today.dayOfMonth.toLong().coerceAtMost(28) step 2) {
            val who = if (d % 3 == 0L) them else me
            val ch = chores[(d % 3).toInt()]
            add(Kind.CHORE_LOG, who, ch.text, d) { it.copy(day = today.minusDays(d).toEpochDay(), extra = ch.id) }
        }

        // Five weeks of weather.
        val skies = Weather.entries
        for (d in 0L until 34L) {
            for ((k, who) in listOf(me, them).withIndex()) {
                if ((d + k) % 6 == 5L) continue
                val w = when {
                    d == 0L -> if (k == 0) Weather.SUN else Weather.PARTLY
                    d in 9L..11L && k == 1 -> Weather.STORM
                    else -> skies[((d * 7 + k * 3 + d / 4) % 4).toInt()]
                }
                val day = today.minusDays(d).toEpochDay()
                add(Kind.WEATHER, who, w.name, d) { it.copy(id = "w-$who-$day", day = day) }
            }
        }

        // Asks.
        add(Kind.ASK, them, Ask.HUG.name)
        add(Kind.ASK, me, Ask.TEA.name)
        add(Kind.ASK, them, Ask.WALK.name, 9) { it.copy(state = 1, archived = true) }

        // Notes and the rest of the door.
        add(Kind.NOTE, them, s("Got you those tangerines. Bottom drawer, don't tell anyone", "Купил тебе тех самых мандаринов. Нижний ящик, никому не говори")) { it.copy(state = 0) }
        add(Kind.NOTE, me, s("Saturday: Mum's at two. Flowers on the way?", "В субботу к маме к двум. Цветы по дороге?"), 2) { it.copy(state = 1) }
        add(Kind.WORRY, them, s("I'm worried about work. Can we talk tonight?", "Мне тревожно из-за работы. Поговорим вечером?"))
        add(Kind.ENVELOPE, me, s("I've been saving for that trip to the sea. It's happening in June.", "Я коплю на то самое море. В июне едем."), 1)
        add(Kind.DRAWING, them, daysAgo = 3) { it.copy(extra = Strokes.encode(drawing())) }
        add(Kind.NOTE, me, s("The plumber comes Thursday morning, I'll be home", "Сантехник в четверг утром, я буду дома"), 9)

        // Thank-yous this month.
        listOf(
            them to s("for the soup when I was ill", "за суп, когда я болел"),
            me to s("for picking me up in the rain", "что встретил под дождём"),
            them to s("for listening", "что выслушала"),
            me to s("for fixing the shelf", "за полку"),
            them to s("for the coffee in bed", "за кофе в постель"),
            me to s("for the tangerines", "за мандарины"),
        ).forEachIndexed { i, (who, text) -> add(Kind.THANKS, who, text, (i * 3L).coerceAtMost(today.dayOfMonth - 1L)) }
        add(Kind.THANKS, them, s("for the long walk", "за долгую прогулку"), 40)

        // Trips.
        add(Kind.TRIP, me, s("Tbilisi", "Тбилиси"), 400) { it.copy(day = today.minusDays(400).toEpochDay()) }
        add(Kind.TRIP, them, s("Kaliningrad", "Калининград"), 200) { it.copy(day = today.minusDays(200).toEpochDay()) }
        add(Kind.TRIP, me, s("Lake Baikal", "Байкал"), 60) { it.copy(day = today.minusDays(60).toEpochDay()) }

        // The hideout.
        add(Kind.WISH, me, s("A film camera", "Плёночный фотоаппарат"))
        add(Kind.WISH, me, s("A day with no plans at all", "День совсем без планов"))
        add(Kind.WISH, them, s("Trail running shoes", "Кроссовки для бега по лесу")) { it.copy(done = true, doneBy = me) }
        add(Kind.WISH, them, s("Lessons in pottery", "Уроки гончарного дела"))

        // Inside: last month's things.
        add(Kind.NOTE, them, s("Welcome home! Dinner's in the oven", "С возвращением! Ужин в духовке"), 33) { it.copy(archived = true, changedAt = at(31)) }
        add(Kind.ENVELOPE, them, s("I'm proud of you. I don't say it enough.", "Я тобой горжусь. Говорю это реже, чем надо."), 36) {
            it.copy(state = 1, day = today.minusDays(35).toEpochDay(), archived = true, changedAt = at(30))
        }
        add(Kind.WORRY, me, s("I feel like we barely see each other this week", "Кажется, мы совсем не видимся на этой неделе"), 34) {
            it.copy(state = 1, archived = true, changedAt = at(33))
        }
        return list
    }

    /** A child-like house under a sun, as a drawing's strokes. */
    private fun drawing(): List<Stroke> {
        val sun = (0..24).map { i -> val a = i * 2 * PI / 24; (0.76f + 0.1f * cos(a).toFloat()) to (0.18f + 0.08f * sin(a).toFloat()) }
        val rays = (0 until 8).map { i ->
            val a = i * PI / 4
            Stroke(4, listOf((0.76f + 0.14f * cos(a).toFloat()) to (0.18f + 0.11f * sin(a).toFloat()), (0.76f + 0.2f * cos(a).toFloat()) to (0.18f + 0.155f * sin(a).toFloat())))
        }
        return listOf(
            Stroke(4, sun),
            Stroke(0, listOf(0.2f to 0.82f, 0.2f to 0.52f, 0.62f to 0.52f, 0.62f to 0.82f, 0.2f to 0.82f)),
            Stroke(1, listOf(0.14f to 0.54f, 0.41f to 0.3f, 0.68f to 0.54f)),
            Stroke(2, listOf(0.27f to 0.6f, 0.37f to 0.6f, 0.37f to 0.69f, 0.27f to 0.69f, 0.27f to 0.6f)),
            Stroke(5, listOf(0.46f to 0.82f, 0.46f to 0.66f, 0.55f to 0.66f, 0.55f to 0.82f)),
            Stroke(3, listOf(0.04f to 0.86f, 0.3f to 0.85f, 0.6f to 0.87f, 0.96f to 0.85f)),
            Stroke(1, listOf(0.82f to 0.84f, 0.8f to 0.7f)),
            Stroke(1, (0..12).map { i -> val a = i * 2 * PI / 12; (0.8f + 0.04f * cos(a).toFloat()) to (0.66f + 0.035f * sin(a).toFloat()) }),
        ) + rays
    }
}
