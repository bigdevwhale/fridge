package app.fridge.ui.pair

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.fridge.R
import app.fridge.container
import app.fridge.data.FridgeSettings
import app.fridge.ui.components.InkButton
import app.fridge.ui.components.Paper
import app.fridge.ui.components.QuietButton
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType

/**
 * Right after pairing: "listen at home in the background?" — yes is the big button, since that is
 * what makes the door shared without opening the app. Answered once; Settings can change it later.
 */
@Composable
fun HumQuestion(settings: FridgeSettings) {
    val p = LocalPalette.current
    val t = LocalType.current
    val c = LocalContext.current.container
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { c.write { this.settings.answerHum(true) } }
    if (settings.humAsked) {
        if (settings.hum) KeepAwake()
        return
    }
    Paper(p.paper, Modifier.fillMaxWidth(), magnet = p.magnetGreen) {
        Text(stringResource(R.string.hum_ask), style = t.title, color = p.ink)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.hum_ask_sub), style = t.body, color = p.muted)
        Spacer(Modifier.height(14.dp))
        InkButton(stringResource(R.string.hum_ask_yes), Modifier.fillMaxWidth(), color = p.magnetGreen) {
            if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
            else c.write { this.settings.answerHum(true) }
        }
        QuietButton(stringResource(R.string.hum_ask_no), Modifier.fillMaxWidth(), onClick = { c.write { this.settings.answerHum(false) } })
    }
}

/**
 * Phones that put apps to sleep (Xiaomi, Huawei, Samsung…) would silence the hum. One tap asks
 * Android to leave the fridge awake; shown only while it isn't.
 */
@SuppressLint("BatteryLife")
@Composable
fun KeepAwake() {
    val p = LocalPalette.current
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var awake by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    // Re-check on return from the system dialog.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) awake = power.isIgnoringBatteryOptimizations(context.packageName) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    if (awake) return
    Column {
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.keep_awake_sub), style = LocalType.current.body, color = p.muted)
        Spacer(Modifier.height(8.dp))
        InkButton(stringResource(R.string.keep_awake)) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
        }
    }
}
