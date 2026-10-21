package app.fridge.ui.pair

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.container
import app.fridge.data.FridgeSettings
import app.fridge.sync.Invite
import app.fridge.sync.Seal
import app.fridge.ui.components.InkButton
import app.fridge.ui.components.Magnet
import app.fridge.ui.components.TopBar
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private enum class Mode { CHOOSE, SHOW, SCANNED, BAD }

/**
 * Pairing, done once: one phone shows a QR, the other scans it. The scanning phone is paired at
 * once; the showing one learns who it is the first time the two meet on the same Wi-Fi.
 */
@Composable
fun PairScreen(settings: FridgeSettings, onBack: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val c = LocalContext.current.container
    var mode by remember { mutableStateOf(if (settings.waiting) Mode.SHOW else Mode.CHOOSE) }
    val scan = rememberLauncherForActivityResult(ScanContract()) { r ->
        val invite = r.contents?.let(Invite::decode)
        when {
            r.contents == null -> Unit
            invite == null -> mode = Mode.BAD
            else -> {
                c.write { this.settings.pair(invite.secret, invite.deviceId, invite.name) }
                mode = Mode.SCANNED
            }
        }
    }

    Column(Modifier.fillMaxSize().background(p.wall)) {
        TopBar(stringResource(R.string.pair_title), onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (mode) {
                Mode.CHOOSE, Mode.BAD -> {
                    if (settings.paired) {
                        Text(stringResource(R.string.pair_already, settings.partnerName.ifBlank { stringResource(R.string.them) }), style = t.body, color = p.ink)
                        Spacer(Modifier.height(14.dp))
                    }
                    Text(stringResource(R.string.pair_how), style = t.body, color = p.muted)
                    Spacer(Modifier.height(24.dp))
                    if (mode == Mode.BAD) {
                        Text(stringResource(R.string.pair_bad), style = t.body, color = p.magnetRed)
                        Spacer(Modifier.height(14.dp))
                    }
                    InkButton(stringResource(R.string.pair_show), Modifier.fillMaxWidth()) {
                        c.write { this.settings.offer(Seal.newSecret()) }
                        mode = Mode.SHOW
                    }
                    Spacer(Modifier.height(10.dp))
                    InkButton(stringResource(R.string.pair_scan), Modifier.fillMaxWidth(), color = p.magnetRed) {
                        scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(true).setPrompt(""))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.pair_privacy), style = t.label, color = p.faint)
                }
                Mode.SHOW -> Show(settings, onBack)
                Mode.SCANNED -> Done(stringResource(R.string.pair_scanned, settings.partnerName.ifBlank { stringResource(R.string.them) }), onBack)
            }
        }
    }
}

@Composable
private fun Show(settings: FridgeSettings, onBack: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    // Drawn from what's stored, so it is the same code every time this screen comes back.
    val qr = remember(settings.secret?.contentHashCode(), settings.deviceId, settings.myName) {
        settings.secret?.let { qr(Invite(it, settings.deviceId, settings.myName).encode(), 720) }
    }
    if (settings.paired) {
        Done(stringResource(R.string.pair_met, settings.partnerName.ifBlank { stringResource(R.string.them) }), onBack)
        return
    }
    Text(stringResource(R.string.pair_show_how), style = t.body, color = p.muted)
    Spacer(Modifier.height(20.dp))
    // The code on a card held up by a magnet above it, never over it: the camera needs every corner.
    Box(Modifier.fillMaxWidth(0.82f)) {
        Box(
            Modifier.padding(top = 14.dp).fillMaxWidth().aspectRatio(1f).shadow(4.dp, RoundedCornerShape(4.dp))
                .background(Color.White, RoundedCornerShape(4.dp)).padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 14.dp),
        ) {
            qr?.let { Image(it.asImageBitmap(), stringResource(R.string.pair_qr), Modifier.fillMaxSize(), filterQuality = FilterQuality.None) }
        }
        Magnet(p.magnetRed, 30.dp, Modifier.align(Alignment.TopCenter))
    }
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.status_waiting), style = t.hand, color = p.blueInk)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.pair_wifi), style = t.label, color = p.faint)
}

@Composable
private fun Done(text: String, onBack: () -> Unit) {
    val p = LocalPalette.current
    Spacer(Modifier.height(30.dp))
    Row(horizontalArrangement = Arrangement.Center) {
        Magnet(p.magnetBlue, 44.dp)
        Spacer(Modifier.width(6.dp))
        Magnet(p.magnetRed, 44.dp)
    }
    Spacer(Modifier.height(18.dp))
    Text(text, style = LocalType.current.title, color = p.ink)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.pair_after), style = LocalType.current.body, color = p.muted)
    Spacer(Modifier.height(24.dp))
    InkButton(stringResource(R.string.to_door), onClick = onBack)
}

fun qr(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"))
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF2E2A24.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
