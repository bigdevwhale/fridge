package app.fridge.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.container
import app.fridge.ui.art.chrome
import app.fridge.ui.art.magnet
import app.fridge.ui.art.tiles
import app.fridge.ui.components.HandField
import app.fridge.ui.components.InkButton
import app.fridge.ui.components.Paper
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType
import app.fridge.ui.theme.Palette

/** A fridge for two: what it is in three lines, and your name to sign notes with. */
@Composable
fun Welcome() {
    val p = LocalPalette.current
    val t = LocalType.current
    val c = LocalContext.current.container
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().drawBehind { tiles(p, 44.dp.toPx()) }.verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(26.dp))
        Canvas(Modifier.size(width = 130.dp, height = 190.dp)) { littleFridge(p) }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.welcome_title), style = t.title, color = p.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        listOf(R.string.welcome_1, R.string.welcome_2, R.string.welcome_3).forEach {
            Text(stringResource(it), style = t.body, color = p.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        Paper(p.yellow, Modifier.fillMaxWidth(0.85f), tilt = -1.5f, magnet = p.magnetRed) {
            Text(stringResource(R.string.welcome_name), style = t.label, color = p.muted)
            HandField(name, { name = it.take(24) }, stringResource(R.string.welcome_name_hint), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
        InkButton(stringResource(R.string.welcome_go), enabled = name.isNotBlank()) {
            val n = name
            c.write { settings.finishWelcome(n) }
        }
        Spacer(Modifier.height(30.dp))
    }
}

/** A small cream fridge with a note under a magnet. */
private fun DrawScope.littleFridge(p: Palette) {
    val w = size.width
    val h = size.height
    drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(6f, 10f), Size(w - 6f, h - 10f), CornerRadius(w * 0.16f))
    drawRoundRect(Brush.horizontalGradient(0f to p.enamel, 0.8f to p.enamel, 1f to p.enamelShade), Offset.Zero, Size(w - 6f, h - 10f), CornerRadius(w * 0.16f))
    drawRect(p.seam, Offset(0f, h * 0.32f), Size(w - 6f, 4f))
    chrome(Offset(w * 0.8f, h * 0.1f), Size(w * 0.05f, h * 0.15f), p)
    chrome(Offset(w * 0.8f, h * 0.4f), Size(w * 0.05f, h * 0.3f), p)
    rotate(-4f, Offset(w * 0.4f, h * 0.55f)) {
        drawRect(Color.Black.copy(alpha = 0.08f), Offset(w * 0.17f, h * 0.43f), Size(w * 0.46f, h * 0.3f))
        drawRect(p.yellow, Offset(w * 0.15f, h * 0.41f), Size(w * 0.46f, h * 0.3f))
        for (i in 0..2) drawLine(p.blueInk.copy(alpha = 0.7f), Offset(w * 0.22f, h * (0.5f + i * 0.06f)), Offset(w * (0.52f - i * 0.06f), h * (0.5f + i * 0.06f)), strokeWidth = 4f)
    }
    magnet(Offset(w * 0.38f, h * 0.42f), w * 0.07f, p.magnetRed)
    magnet(Offset(w * 0.25f, h * 0.15f), w * 0.06f, p.magnetBlue)
    magnet(Offset(w * 0.5f, h * 0.2f), w * 0.05f, p.magnetYellow)
}
