package app.fridge.ui.door

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.data.Item
import app.fridge.domain.Ask
import app.fridge.domain.Weather
import app.fridge.ui.art.Art
import app.fridge.ui.art.chrome
import app.fridge.ui.art.heart
import app.fridge.ui.art.magnet
import app.fridge.ui.art.weather
import app.fridge.ui.components.WordMagnet
import app.fridge.ui.components.magnetColors
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The top door: the nameplate, today's weather for each of you, asks, thanks, and trip magnets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Freezer(state: DoorState, open: (Sheet) -> Unit) {
    val p = LocalPalette.current
    val b = state.board
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 40.dp, top = 22.dp, bottom = 22.dp)) {
            Nameplate(state)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                WeatherMagnet(b.myName.ifBlank { stringResource(R.string.me) }, b.weatherOf(b.me), inkOf(b, b.me), mine = true) { open(Sheet.Weather) }
                if (b.paired) WeatherMagnet(b.partnerName.ifBlank { stringResource(R.string.them) }, b.weatherOf(b.partner), inkOf(b, b.partner), mine = false) {}
            }
            if (b.asks.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // The other one's asks first, and big: they're what this door is for today.
                    b.asks.sortedBy { it.author == b.me }.forEach { a ->
                        val mine = a.author == b.me
                        WordMagnet(
                            askText(a.text), tilt = Art.tilt(a.id, 4f), ink = inkOf(b, a.author), big = !mine,
                            onClick = { open(Sheet.Open(a.id)) },
                        )
                    }
                }
            }
            Thanks(state, open)
            if (b.trips.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    b.trips.forEach { TripMagnet(it) { open(Sheet.Open(it.id)) } }
                }
            }
        }
        // The freezer's short handle.
        Box(
            Modifier.align(Alignment.BottomEnd).padding(bottom = 18.dp, end = 10.dp).size(width = 22.dp, height = 70.dp)
                .drawBehind { chrome(Offset(size.width * 0.25f, 0f), Size(size.width * 0.5f, size.height), p) },
        )
    }
}

/** The chrome nameplate, like the maker's badge on an old fridge — with your two names on it. */
@Composable
private fun Nameplate(state: DoorState) {
    val p = LocalPalette.current
    val b = state.board
    val text = when {
        b.paired && b.myName.isNotBlank() && b.partnerName.isNotBlank() -> stringResource(R.string.plate_two, b.myName, b.partnerName)
        else -> stringResource(R.string.plate_one)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .shadow(3.dp, RoundedCornerShape(50))
                .drawBehind { chrome(Offset.Zero, size, p, size.height / 2) }
                .padding(horizontal = 26.dp, vertical = 6.dp),
        ) {
            Text(text, style = LocalType.current.plate, color = lerp(p.chromeDark, Color.Black, 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One person's weather today: a big round magnet with the sky drawn on it, their name under it. */
@Composable
private fun WeatherMagnet(name: String, w: Weather?, ink: Color, mine: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val label = w?.let { weatherName(it) } ?: stringResource(if (mine) R.string.weather_set else R.string.weather_none)
    Column(
        Modifier.widthIn(max = 130.dp).clip(RoundedCornerShape(12.dp)).let { if (mine) it.clickable(onClick = onClick) else it }.padding(6.dp)
            .semantics { contentDescription = "$name: $label" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(78.dp)) {
            val r = size.minDimension / 2.2f
            magnet(center, r, Color(0xFFFDFBF6))
            if (w != null) {
                val box = r * 1.45f
                weather(w, center - Offset(box / 2, box / 2), box, p)
            } else {
                drawCircle(p.faint.copy(alpha = 0.5f), r * 0.55f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            }
        }
        Text(name, style = LocalType.current.handSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = LocalType.current.label, color = p.muted, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** This month's thank-yous: small round magnets with a heart, gathering on the freezer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Thanks(state: DoorState, open: (Sheet) -> Unit) {
    val b = state.board
    val month = b.thisMonthThanks
    if (month.isEmpty()) return
    val p = LocalPalette.current
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.thanks_month, month.size), style = LocalType.current.label, color = p.muted)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        month.forEach { t ->
            val desc = t.text
            Box(
                Modifier.size(34.dp).clip(CircleShape).clickable { open(Sheet.Open(t.id)) }.semantics { contentDescription = desc },
            ) {
                Canvas(Modifier.size(34.dp)) {
                    magnet(center, size.minDimension / 2.4f, if (b.isBlue(t.author)) p.magnetBlue else p.magnetRed)
                    heart(center + Offset(0f, 1f), size.minDimension * 0.12f, Color.White.copy(alpha = 0.9f))
                }
                if (t.isNew(b)) NewDot(Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

/** A souvenir magnet: the place in script on a coloured plaque, the year under it. */
@Composable
private fun TripMagnet(t: Item, onClick: () -> Unit) {
    val colors = magnetColors()
    val color = colors[Art.seed(t.id) % colors.size]
    val year = if (t.day != 0L) LocalDate.ofEpochDay(t.day).year.toString() else ""
    Column(
        Modifier
            .rotate(Art.tilt(t.id, 5f))
            .shadow(3.dp, RoundedCornerShape(10.dp))
            .background(color, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t.text, style = LocalType.current.plate.copy(fontSize = LocalType.current.plate.fontSize * 0.75f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
        if (year.isNotEmpty()) Text(year, style = LocalType.current.label, color = Color.White.copy(alpha = 0.85f))
    }
}

/** A small red dot: from the other one, not looked at yet. */
@Composable
fun NewDot(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(modifier.size(10.dp).shadow(1.dp, CircleShape).background(p.magnetRed, CircleShape))
}

@Composable
fun weatherName(w: Weather): String = stringResource(
    when (w) {
        Weather.SUN -> R.string.weather_sun
        Weather.PARTLY -> R.string.weather_partly
        Weather.WIND -> R.string.weather_wind
        Weather.FOG -> R.string.weather_fog
        Weather.RAIN -> R.string.weather_rain
        Weather.STORM -> R.string.weather_storm
    },
)

@Composable
fun askText(name: String): String = stringResource(
    when (runCatching { Ask.valueOf(name) }.getOrDefault(Ask.HUG)) {
        Ask.HUG -> R.string.ask_hug
        Ask.QUIET -> R.string.ask_quiet
        Ask.TALK -> R.string.ask_talk
        Ask.TEA -> R.string.ask_tea
        Ask.WALK -> R.string.ask_walk
        Ask.GENTLE -> R.string.ask_gentle
    },
)

fun dayText(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
