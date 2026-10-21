package app.fridge.ui.inside

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.container
import app.fridge.data.FridgeSettings
import app.fridge.domain.Board
import app.fridge.domain.Chores
import app.fridge.ui.art.weather
import app.fridge.ui.components.TopBar
import app.fridge.ui.door.DoorState
import app.fridge.ui.door.LocalBehind
import app.fridge.ui.door.PaperOnDoor
import app.fridge.ui.door.Sheet
import app.fridge.ui.door.Sheets
import app.fridge.ui.door.inkOf
import app.fridge.ui.door.nameOf
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as MonthStyle
import java.util.Locale

/**
 * Inside the fridge, under its little lamp: everything that came off the door, on shelves by month,
 * with the weather you both had and how the chores went. Nothing in here is ever lost, only kept.
 */
@Composable
fun InsideScreen(settings: FridgeSettings, onBack: () -> Unit, onPair: () -> Unit) {
    val c = LocalContext.current.container
    val items by c.db.dao().live().collectAsState(initial = emptyList())
    val near by c.hum.near.collectAsState()
    val now = remember { System.currentTimeMillis() }
    val board = remember(items, settings) { Board.of(items, settings, now) }
    InsideContent(DoorState(board, near, settings, now), onBack, onPair)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsideContent(state: DoorState, onBack: () -> Unit, onPair: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val b = state.board
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    Box(
        Modifier.fillMaxSize().background(p.inside).drawBehind {
            // The lamp at the top, warm against the cold white.
            drawRect(Brush.radialGradient(listOf(p.lamp.copy(alpha = 0.75f), Color.Transparent), center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.9f))
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.inside), onBack)
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 18.dp)) {
                Shelf(stringResource(R.string.inside_weather)) { WeatherWeeks(state) }
                Shelf(stringResource(R.string.inside_chores)) { ChoreMonths(state) }
                val thanks = b.thanks.groupBy { YearMonth.from(it.date) }.toSortedMap(compareByDescending { it })
                if (thanks.isNotEmpty()) {
                    Shelf(stringResource(R.string.inside_thanks)) {
                        thanks.forEach { (month, list) ->
                            Text("${monthName(month)} · ${list.size}", style = t.label, color = p.muted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                                list.forEach { th -> Text(th.text, style = t.handSmall, color = inkOf(b, th.author)) }
                            }
                        }
                    }
                }
                val off = b.archive.filter { it.type in Board.PAPERS }.groupBy { YearMonth.from(java.time.Instant.ofEpochMilli(it.changedAt).atZone(java.time.ZoneId.systemDefault())) }
                if (off.isEmpty()) {
                    Shelf(stringResource(R.string.inside_off)) {
                        Text(stringResource(R.string.inside_off_empty), style = t.hand, color = p.faint)
                    }
                }
                off.forEach { (month, list) ->
                    Shelf(stringResource(R.string.inside_off_month, monthName(month))) {
                        val papers = list
                        CompositionLocalProvider(LocalBehind provides p.inside) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                for (col in 0..1) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        papers.filterIndexed { i, _ -> i % 2 == col }.forEach { PaperOnDoor(it, state) { sheet = Sheet.Open(it.id) } }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
        sheet?.let { s -> Sheets(s, state, onClose = { sheet = null }, onOpen = { sheet = it }, onPair = onPair) }
    }
}

/** A glass shelf with what's on it. */
@Composable
private fun Shelf(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Spacer(Modifier.height(18.dp))
    Text(title.uppercase(), style = LocalType.current.label, color = p.muted)
    Spacer(Modifier.height(8.dp))
    content()
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(6.dp).background(Brush.verticalGradient(listOf(Color.White, p.shelf, p.shelf.copy(alpha = 0.3f)))))
}

/** Five weeks of weather, one small calendar each, side by side. */
@Composable
private fun WeatherWeeks(state: DoorState) {
    val p = LocalPalette.current
    val b = state.board
    val people = listOfNotNull(b.me, b.partner.takeIf { b.paired })
    // Weeks start on Monday; the last row ends with this week.
    val today = LocalDate.ofEpochDay(b.today)
    val start = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(4)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        people.forEach { who ->
            Column(Modifier.weight(1f)) {
                Text(nameOf(b, who), style = LocalType.current.handSmall, color = inkOf(b, who))
                for (week in 0 until 5) {
                    Row {
                        for (d in 0 until 7) {
                            val day = start.plusDays((week * 7 + d).toLong())
                            val w = b.weatherOf(who, day.toEpochDay())
                            Canvas(Modifier.weight(1f).aspectRatio(1f).padding(1.dp)) {
                                drawRect(if (day.isAfter(today)) Color.Transparent else Color.White.copy(alpha = 0.6f))
                                if (w != null) weather(w, Offset.Zero, size.minDimension, p)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The tally of chores for the last three months, as plain numbers. */
@Composable
private fun ChoreMonths(state: DoorState) {
    val p = LocalPalette.current
    val b = state.board
    val today = LocalDate.ofEpochDay(b.today)
    val people = listOfNotNull(b.me, b.partner.takeIf { b.paired }).map { it to nameOf(b, it) }
    for (back in 0L until 3L) {
        val month = YearMonth.from(today).minusMonths(back)
        val tally = Chores.balance(b.choreLogs, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(monthName(month), style = LocalType.current.body, color = p.ink, modifier = Modifier.width(120.dp))
            Text(
                if (tally.isEmpty()) "—" else people.joinToString("   ") { (who, name) -> "$name ${tally[who] ?: 0}" },
                style = LocalType.current.body, color = p.muted,
            )
        }
    }
}

@Composable
private fun monthName(m: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val name = m.month.getDisplayName(MonthStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
    return if (m.year == YearMonth.now().year) name else "$name ${m.year}"
}
