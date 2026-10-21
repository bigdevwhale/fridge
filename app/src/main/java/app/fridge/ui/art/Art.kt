package app.fridge.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import app.fridge.domain.Weather
import app.fridge.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Everything on the fridge is drawn here, in code: tiles, enamel, chrome, magnets, the weather. */
object Art {
    /** Set by the screen renders: no animation, so a frame can be caught still. */
    @Volatile var still = false

    /** A stable small number from an id: how a note is tilted, which magnet holds it. */
    fun seed(id: String): Int = id.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }

    fun tilt(id: String, max: Float = 3f): Float = ((seed(id) % 1000) / 1000f * 2f - 1f) * max
}

/** The kitchen wall: pale square tiles with soft grout. */
fun DrawScope.tiles(p: Palette, tile: Float) {
    drawRect(p.wall)
    var x = 0f
    while (x < size.width) {
        drawLine(p.grout, Offset(x, 0f), Offset(x, size.height), strokeWidth = tile * 0.035f)
        x += tile
    }
    var y = 0f
    while (y < size.height) {
        drawLine(p.grout, Offset(0f, y), Offset(size.width, y), strokeWidth = tile * 0.035f)
        y += tile
    }
}

/** A round magnet: a shadow under it, a rim, a dome, and the window's light on top. */
fun DrawScope.magnet(center: Offset, r: Float, color: Color) {
    drawCircle(Color.Black.copy(alpha = 0.16f), r * 1.02f, center + Offset(r * 0.12f, r * 0.2f))
    drawCircle(lerp(color, Color.Black, 0.25f), r, center)
    drawCircle(
        Brush.radialGradient(listOf(lerp(color, Color.White, 0.25f), color), center = center + Offset(-r * 0.3f, -r * 0.35f), radius = r * 1.2f),
        r * 0.86f, center,
    )
    drawOval(Color.White.copy(alpha = 0.55f), topLeft = center + Offset(-r * 0.5f, -r * 0.62f), size = Size(r * 0.55f, r * 0.32f))
}

/** A chrome bar, lit from the window on the left. */
fun DrawScope.chrome(topLeft: Offset, s: Size, p: Palette, radius: Float = s.minDimension / 2) {
    drawRoundRect(Color.Black.copy(alpha = 0.14f), topLeft + Offset(2f, 4f), s, CornerRadius(radius))
    drawRoundRect(
        Brush.horizontalGradient(listOf(p.chromeDark, p.chromeLight, p.chrome, p.chromeDark), startX = topLeft.x, endX = topLeft.x + s.width),
        topLeft, s, CornerRadius(radius),
    )
}

/** The day's weather, drawn in a [box]-sized square at [o]. */
fun DrawScope.weather(w: Weather, o: Offset, box: Float, p: Palette) {
    val c = o + Offset(box / 2, box / 2)
    val sun = Color(0xFFF2B630)
    val cloud = Color(0xFFF7F7F4)
    val cloudEdge = Color(0xFF9AA7B0)
    val rain = Color(0xFF3E7CB1)
    fun sunAt(at: Offset, r: Float) {
        for (i in 0 until 8) {
            val a = i * PI / 4
            drawLine(sun, at + Offset((cos(a) * r * 1.35f).toFloat(), (sin(a) * r * 1.35f).toFloat()), at + Offset((cos(a) * r * 1.75f).toFloat(), (sin(a) * r * 1.75f).toFloat()), strokeWidth = r * 0.22f, cap = StrokeCap.Round)
        }
        drawCircle(sun, r, at)
    }
    fun cloudAt(at: Offset, w: Float, dark: Boolean = false) {
        val fill = if (dark) Color(0xFF7D8892) else cloud
        val path = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(at.x - w * 0.5f, at.y - w * 0.05f, at.x + w * 0.5f, at.y + w * 0.3f))
            addOval(androidx.compose.ui.geometry.Rect(at.x - w * 0.38f, at.y - w * 0.28f, at.x + w * 0.02f, at.y + w * 0.12f))
            addOval(androidx.compose.ui.geometry.Rect(at.x - w * 0.12f, at.y - w * 0.42f, at.x + w * 0.36f, at.y + w * 0.08f))
        }
        drawPath(path, fill)
        drawPath(path, cloudEdge, style = Stroke(w * 0.035f))
    }
    when (w) {
        Weather.SUN -> sunAt(c, box * 0.2f)
        Weather.PARTLY -> {
            sunAt(c + Offset(-box * 0.12f, -box * 0.12f), box * 0.15f)
            cloudAt(c + Offset(box * 0.08f, box * 0.1f), box * 0.62f)
        }
        Weather.WIND -> for (i in 0 until 3) {
            val y = c.y - box * 0.2f + i * box * 0.2f
            val path = Path().apply {
                moveTo(c.x - box * 0.36f, y)
                cubicTo(c.x - box * 0.1f, y - box * 0.1f, c.x + box * 0.1f, y + box * 0.1f, c.x + box * (0.3f - i * 0.06f), y - box * 0.02f)
            }
            drawPath(path, cloudEdge, style = Stroke(box * 0.05f, cap = StrokeCap.Round))
        }
        Weather.FOG -> for (i in 0 until 4) {
            val y = c.y - box * 0.22f + i * box * 0.15f
            val inset = if (i % 2 == 0) 0.05f else 0.12f
            drawLine(cloudEdge, Offset(o.x + box * (0.14f + inset), y), Offset(o.x + box * (0.86f - inset), y), strokeWidth = box * 0.06f, cap = StrokeCap.Round)
        }
        Weather.RAIN -> {
            cloudAt(c + Offset(0f, -box * 0.08f), box * 0.7f)
            for (i in 0 until 3) {
                val x = c.x - box * 0.18f + i * box * 0.18f
                drawLine(rain, Offset(x, c.y + box * 0.2f), Offset(x - box * 0.04f, c.y + box * 0.34f), strokeWidth = box * 0.05f, cap = StrokeCap.Round)
            }
        }
        Weather.STORM -> {
            cloudAt(c + Offset(0f, -box * 0.1f), box * 0.7f, dark = true)
            val bolt = Path().apply {
                moveTo(c.x + box * 0.02f, c.y + box * 0.08f)
                lineTo(c.x - box * 0.1f, c.y + box * 0.26f)
                lineTo(c.x + box * 0.0f, c.y + box * 0.26f)
                lineTo(c.x - box * 0.06f, c.y + box * 0.42f)
                lineTo(c.x + box * 0.14f, c.y + box * 0.2f)
                lineTo(c.x + box * 0.03f, c.y + box * 0.2f)
                close()
            }
            drawPath(bolt, sun)
        }
    }
}

/** A sealed envelope filling the draw area, with a wax seal where the flap meets. */
fun DrawScope.envelope(p: Palette, open: Boolean) {
    val w = size.width
    val h = size.height
    drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(3f, 6f), size, CornerRadius(6f))
    drawRoundRect(p.kraft, Offset.Zero, size, CornerRadius(6f))
    val edge = lerp(p.kraft, Color.Black, 0.18f)
    // The bottom folds.
    drawPath(Path().apply { moveTo(0f, h); lineTo(w / 2, h * 0.5f); lineTo(w, h) }, edge, style = Stroke(1.5f))
    if (open) {
        drawPath(Path().apply { moveTo(0f, 0f); lineTo(w / 2, -h * 0.4f); lineTo(w, 0f); close() }, lerp(p.kraft, Color.White, 0.2f))
        return
    }
    val flap = Path().apply { moveTo(0f, 0f); lineTo(w / 2, h * 0.55f); lineTo(w, 0f); close() }
    drawPath(flap, lerp(p.kraft, Color.White, 0.12f))
    drawPath(flap, edge, style = Stroke(1.5f))
    seal(Offset(w / 2, h * 0.55f), h * 0.16f, p)
}

/** A wax seal: an irregular red blot with a pressed ring and a heart. */
fun DrawScope.seal(c: Offset, r: Float, p: Palette) {
    val blot = Path().apply {
        for (i in 0..18) {
            val a = i * 2 * PI / 18
            val rr = r * (1f + 0.08f * sin(i * 2.7f))
            val pt = c + Offset((cos(a) * rr).toFloat(), (sin(a) * rr).toFloat())
            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
        }
        close()
    }
    translate(r * 0.08f, r * 0.14f) { drawPath(blot, Color.Black.copy(alpha = 0.18f)) }
    drawPath(blot, p.wax)
    drawCircle(lerp(p.wax, Color.Black, 0.25f), r * 0.68f, c, style = Stroke(r * 0.1f))
    heart(c, r * 0.36f, lerp(p.wax, Color.Black, 0.3f))
}

fun DrawScope.heart(c: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.6f, c.y - r * 0.1f, c.x - r * 0.7f, c.y - r * 1.3f, c.x, c.y - r * 0.4f)
        cubicTo(c.x + r * 0.7f, c.y - r * 1.3f, c.x + r * 1.6f, c.y - r * 0.1f, c.x, c.y + r * 0.9f)
        close()
    }
    drawPath(path, color)
}

/** A paper corner curling up with age (0..1). */
fun DrawScope.curl(amount: Float, paper: Color, behind: Color) {
    if (amount <= 0f) return
    val d = size.minDimension * 0.22f * amount
    val w = size.width
    val h = size.height
    drawPath(Path().apply { moveTo(w - d, h); lineTo(w, h - d); lineTo(w, h); close() }, behind)
    drawPath(Path().apply { moveTo(w - d, h); lineTo(w, h - d); lineTo(w - d * 0.85f, h - d * 0.85f); close() }, lerp(paper, Color.Black, 0.12f))
}
