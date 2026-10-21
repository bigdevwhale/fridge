package app.fridge.ui.door

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.data.Item
import app.fridge.data.Kind
import app.fridge.domain.Stroke as Line
import app.fridge.domain.Strokes
import app.fridge.ui.art.Art
import app.fridge.ui.art.curl
import app.fridge.ui.art.envelope
import app.fridge.ui.art.heart
import app.fridge.ui.components.Magnet
import app.fridge.ui.components.magnetColors
import app.fridge.ui.theme.Kitchen
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType
import app.fridge.ui.theme.Palette

/** What a paper hangs on — the enamel door, or the inside of the fridge — for its curled corner to show. */
val LocalBehind = staticCompositionLocalOf { Kitchen.enamel }

/** Notes, worries, envelopes and drawings, in two loose columns down the door. */
@Composable
fun Papers(state: DoorState, open: (Sheet) -> Unit) {
    val papers = state.board.papers.sortedByDescending { it.createdAt }
    if (papers.isEmpty()) {
        EmptyDoor()
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for (col in 0..1) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                papers.filterIndexed { i, _ -> i % 2 == col }.forEach { PaperOnDoor(it, state) { open(Sheet.Open(it.id)) } }
            }
        }
    }
}

@Composable
private fun EmptyDoor() {
    val p = LocalPalette.current
    Text(
        stringResource(R.string.door_empty), style = LocalType.current.hand, color = p.faint,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp),
    )
}

/** A paper's colour: one of four, chosen by its id so it's the same on both doors. */
fun noteColor(p: Palette, id: String): Color = listOf(p.yellow, p.pink, p.sky, p.mint)[Art.seed(id) % 4]

/** How far a paper has curled and yellowed: a week on the door curls a corner, a month yellows it. */
fun age(item: Item, now: Long): Float {
    val days = (now - item.createdAt) / 86_400_000f
    return when {
        days < 7 -> 0f
        days < 30 -> 0.5f
        else -> 1f
    }
}

@Composable
fun PaperOnDoor(item: Item, state: DoorState, onClick: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val b = state.board
    val colors = magnetColors()
    val magnet = colors[Art.seed(item.id) / 7 % colors.size]
    val tilt = Art.tilt(item.id, 3.5f)
    val aged = age(item, state.now)
    Box(Modifier.rotate(tilt)) {
        when (item.type) {
            Kind.ENVELOPE -> if (item.state == 0) {
                Column(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1.55f)) { envelope(p, open = false) }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.envelope_from, nameOf(b, item.author)), style = t.handSmall, color = inkOf(b, item.author))
                }
            } else {
                PaperSheet(lerp(p.paper, p.kraft, 0.15f), aged, onClick) {
                    Text(stringResource(R.string.envelope_opened), style = t.label, color = p.faint)
                    Text(item.text, style = t.hand, color = inkOf(b, item.author), maxLines = 6, overflow = TextOverflow.Ellipsis)
                    Sign(item, state)
                }
            }
            Kind.WORRY -> if (item.state == 0) {
                // Face down: the back of the paper, with just a hint that there's something on the other side.
                PaperSheet(Color(0xFFE9E3D6), aged, onClick, back = true) {
                    Text(stringResource(R.string.worry_back), style = t.hand, color = p.muted)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.worry_from, nameOf(b, item.author)), style = t.handSmall, color = inkOf(b, item.author))
                }
            } else {
                PaperSheet(p.paper, aged, onClick) {
                    Text(stringResource(R.string.worry_turned), style = t.label, color = p.faint)
                    Text(item.text, style = t.hand, color = inkOf(b, item.author), maxLines = 6, overflow = TextOverflow.Ellipsis)
                    Sign(item, state)
                }
            }
            Kind.DRAWING -> PaperSheet(p.paper, aged, onClick, squared = true) {
                Drawing(Strokes.decode(item.extra), Modifier.fillMaxWidth().aspectRatio(0.8f))
                Sign(item, state)
            }
            else -> PaperSheet(noteColor(p, item.id), aged, onClick) {
                Text(item.text, style = t.hand, color = inkOf(b, item.author), maxLines = 8, overflow = TextOverflow.Ellipsis)
                Sign(item, state)
            }
        }
        if (item.type != Kind.ENVELOPE || item.state == 1) Magnet(magnet, 26.dp, Modifier.align(Alignment.TopCenter))
        if (item.isNew(b)) NewDot(Modifier.align(Alignment.TopEnd).padding(top = 4.dp))
    }
}

@Composable
private fun PaperSheet(color: Color, aged: Float, onClick: () -> Unit, back: Boolean = false, squared: Boolean = false, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val behind = LocalBehind.current
    val paper = lerp(color, p.kraft, 0.18f * aged)
    Column(
        Modifier
            .padding(top = 9.dp)
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(2.dp))
            .background(paper, RoundedCornerShape(2.dp))
            .drawWithContent {
                if (squared) squares(p.paperLine.copy(alpha = 0.6f))
                if (back) {
                    // The faint show-through of writing on the other side.
                    var y = 26.dp.toPx()
                    while (y < size.height - 10.dp.toPx()) {
                        drawLine(Color.Black.copy(alpha = 0.035f), Offset(14.dp.toPx(), y), Offset(size.width - 18.dp.toPx(), y + 2f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                        y += 14.dp.toPx()
                    }
                }
                drawContent()
                curl(aged, paper, behind)
            }
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
    ) { content() }
}

/** The small signature under a note: who, and the little marks it gathered. */
@Composable
fun Sign(item: Item, state: DoorState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val b = state.board
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("— " + nameOf(b, item.author), style = t.handSmall, color = p.muted, modifier = Modifier.weight(1f), maxLines = 1)
        if (item.showAt > state.now) Text(stringResource(R.string.note_morning_mark), style = t.label, color = p.faint)
        if (item.type == Kind.NOTE && item.state == 1) {
            Canvas(Modifier.size(18.dp)) { heart(center, size.minDimension * 0.4f, p.magnetRed) }
        }
    }
}

/** The squared paper of a school exercise book. */
fun DrawScope.squares(line: Color) {
    val step = 14.dp.toPx()
    var x = step
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

/** The crayons, in the order a drawing's strokes number them. */
@Composable
fun crayons(): List<Color> {
    val p = LocalPalette.current
    return listOf(Color(0xFF2E2A24), p.magnetRed, p.magnetBlue, p.magnetGreen, p.magnetYellow, p.magnetPurple)
}

/** A drawing's strokes, drawn in waxy crayon lines. */
@Composable
fun Drawing(lines: List<Line>, modifier: Modifier = Modifier) {
    val colors = crayons()
    val paths = remember(lines) { lines }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        paths.forEach { s ->
            val color = colors[s.crayon.coerceIn(0, colors.size - 1)]
            if (s.points.size == 1) {
                drawCircle(color, 3.dp.toPx(), Offset(s.points[0].first * w, s.points[0].second * h))
                return@forEach
            }
            val path = Path()
            s.points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * w, y * h) else path.lineTo(x * w, y * h) }
            drawPath(path, color.copy(alpha = 0.9f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
