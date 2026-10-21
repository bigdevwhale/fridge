package app.fridge.domain

/**
 * A drawing as plain text, small enough to travel: strokes separated by "|", each one a crayon
 * number, ";", and its points as x,y in thousandths of the paper's width and height.
 */
data class Stroke(val crayon: Int, val points: List<Pair<Float, Float>>)

object Strokes {
    fun encode(strokes: List<Stroke>): String = strokes.filter { it.points.isNotEmpty() }.joinToString("|") { s ->
        s.crayon.toString() + ";" + s.points.joinToString(" ") { (x, y) -> "${(x * 1000).toInt().coerceIn(0, 1000)},${(y * 1000).toInt().coerceIn(0, 1000)}" }
    }

    fun decode(text: String): List<Stroke> = text.split("|").mapNotNull { part ->
        val semi = part.indexOf(';')
        if (semi < 0) return@mapNotNull null
        val crayon = part.substring(0, semi).toIntOrNull() ?: return@mapNotNull null
        val points = part.substring(semi + 1).split(" ").mapNotNull { xy ->
            val c = xy.indexOf(',')
            if (c < 0) null else {
                val x = xy.substring(0, c).toIntOrNull()
                val y = xy.substring(c + 1).toIntOrNull()
                if (x == null || y == null) null else x / 1000f to y / 1000f
            }
        }
        if (points.isEmpty()) null else Stroke(crayon, points)
    }
}
