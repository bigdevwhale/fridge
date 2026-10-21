package app.fridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.fridge.R

/**
 * A kitchen in the late afternoon: pale mint tiles, a cream enamel fridge with chrome, paper notes in
 * two inks — one for each of you — and bright magnets. Always light; it is a kitchen.
 */
@Immutable
data class Palette(
    val wall: Color,
    val grout: Color,
    val enamel: Color,
    val enamelShade: Color,
    val seam: Color,
    val chrome: Color,
    val chromeDark: Color,
    val chromeLight: Color,
    val floor: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val paper: Color,
    val paperLine: Color,
    val yellow: Color,
    val pink: Color,
    val sky: Color,
    val mint: Color,
    /** The two inks: the phone with the smaller id writes in blue, the other in red, on both doors. */
    val blueInk: Color,
    val redInk: Color,
    val magnetRed: Color,
    val magnetYellow: Color,
    val magnetBlue: Color,
    val magnetGreen: Color,
    val magnetOrange: Color,
    val magnetPurple: Color,
    val wax: Color,
    val kraft: Color,
    val lamp: Color,
    val inside: Color,
    val shelf: Color,
)

val Kitchen = Palette(
    wall = Color(0xFFDDE7E0),
    grout = Color(0xFFCAD7CF),
    enamel = Color(0xFFF5F0E5),
    enamelShade = Color(0xFFE3DAC8),
    seam = Color(0xFFC9BEA9),
    chrome = Color(0xFFCDD3D7),
    chromeDark = Color(0xFF8C959C),
    chromeLight = Color(0xFFF7F9FA),
    floor = Color(0xFFB98F68),
    ink = Color(0xFF2E2A24),
    muted = Color(0xFF6B6457),
    faint = Color(0xFF9C9486),
    paper = Color(0xFFFFFDF5),
    paperLine = Color(0xFFBFD3E3),
    yellow = Color(0xFFFBE7A1),
    pink = Color(0xFFF8D3D0),
    sky = Color(0xFFD1E4F2),
    mint = Color(0xFFD7EBCB),
    blueInk = Color(0xFF2C4A8C),
    redInk = Color(0xFFA6363F),
    magnetRed = Color(0xFFD9483B),
    magnetYellow = Color(0xFFF2B630),
    magnetBlue = Color(0xFF3E7CB1),
    magnetGreen = Color(0xFF4E9A5B),
    magnetOrange = Color(0xFFE9823A),
    magnetPurple = Color(0xFF8C5BA6),
    wax = Color(0xFFB02E2E),
    kraft = Color(0xFFE2C9A0),
    lamp = Color(0xFFFFE7A8),
    inside = Color(0xFFEFF3F1),
    shelf = Color(0xFFD5E0DC),
)

val LocalPalette = staticCompositionLocalOf { Kitchen }

/** Handwriting for everything the two of you write; a rounded sans for the app's own words; script for chrome. */
val Hand = FontFamily(Font(R.font.caveat, FontWeight.Normal))
val Script = FontFamily(Font(R.font.lobster, FontWeight.Normal))
val Sans = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

@Immutable
data class Type(
    val hand: TextStyle,
    val handBig: TextStyle,
    val handSmall: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val plate: TextStyle,
)

private val Types = Type(
    hand = TextStyle(fontFamily = Hand, fontSize = 23.sp, lineHeight = 26.sp),
    handBig = TextStyle(fontFamily = Hand, fontSize = 30.sp, lineHeight = 34.sp),
    handSmall = TextStyle(fontFamily = Hand, fontSize = 19.sp, lineHeight = 21.sp),
    title = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    body = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 22.sp),
    label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.3.sp),
    plate = TextStyle(fontFamily = Script, fontSize = 24.sp, lineHeight = 28.sp),
)

val LocalType = staticCompositionLocalOf { Types }

@Composable
fun FridgeTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides Kitchen, LocalType provides Types) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Kitchen.blueInk,
                onPrimary = Color.White,
                secondary = Kitchen.magnetRed,
                background = Kitchen.wall,
                surface = Kitchen.enamel,
                surfaceVariant = Kitchen.paper,
                surfaceContainerLow = Kitchen.enamel,
                surfaceContainer = Kitchen.enamel,
                surfaceContainerHigh = Kitchen.enamel,
                outline = Kitchen.seam,
                onBackground = Kitchen.ink,
                onSurface = Kitchen.ink,
                onSurfaceVariant = Kitchen.muted,
            ),
            content = content,
        )
    }
}
