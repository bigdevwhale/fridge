package app.fridge.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import app.fridge.BuildConfig
import app.fridge.R
import app.fridge.container
import app.fridge.data.FridgeSettings
import app.fridge.sync.Parcel
import app.fridge.ui.components.CommunityLinks
import app.fridge.ui.components.HandField
import app.fridge.ui.components.InkButton
import app.fridge.ui.components.Paper
import app.fridge.ui.components.QuietButton
import app.fridge.ui.components.SectionTitle
import app.fridge.ui.components.TopBar
import app.fridge.ui.pair.KeepAwake
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Your name, the other phone, the background hum, the parcel for when you're apart, the language. */
@Composable
fun SettingsScreen(settings: FridgeSettings, onBack: () -> Unit, onPair: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val p = LocalPalette.current
    val t = LocalType.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(settings.myName) }
    var forgetting by remember { mutableStateOf(false) }
    var parcelNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(name) {
        delay(500)
        if (name.trim() != settings.myName) c.settings.setMyName(name)
    }

    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { c.write { this.settings.setHum(true) } }
    val strings = object {
        val took = stringResource(R.string.parcel_took)
        val nothing = stringResource(R.string.parcel_nothing)
        val notOurs = stringResource(R.string.parcel_not_ours)
        val own = stringResource(R.string.parcel_own)
    }
    val openParcel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    Parcel.unpack(bytes, c.db.dao(), c.settings)
                }.getOrDefault(Parcel.Opened.NotOurs)
            }
            parcelNote = when (r) {
                is Parcel.Opened.Took -> if (r.news.isEmpty()) strings.nothing else strings.took.format(r.news.size)
                Parcel.Opened.NotOurs -> strings.notOurs
                Parcel.Opened.OwnParcel -> strings.own
            }
        }
    }

    Column(Modifier.fillMaxSize().background(p.wall)) {
        TopBar(stringResource(R.string.settings), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            SectionTitle(stringResource(R.string.set_you), stringResource(R.string.set_you_sub))
            Paper(p.paper) {
                HandField(name, { name = it.take(24) }, stringResource(R.string.welcome_name_hint), singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            SectionTitle(stringResource(R.string.set_partner))
            when {
                settings.paired -> {
                    Text(stringResource(R.string.set_paired, settings.partnerName.ifBlank { stringResource(R.string.them) }), style = t.body, color = p.ink)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        QuietButton(stringResource(R.string.set_repair), color = p.blueInk, onClick = onPair)
                        QuietButton(stringResource(R.string.set_forget), color = p.magnetRed, onClick = { forgetting = true })
                    }
                }
                settings.waiting -> {
                    Text(stringResource(R.string.status_waiting), style = t.body, color = p.ink)
                    Spacer(Modifier.height(8.dp))
                    InkButton(stringResource(R.string.pair_show_again), onClick = onPair)
                }
                else -> {
                    Text(stringResource(R.string.set_unpaired), style = t.body, color = p.muted)
                    Spacer(Modifier.height(10.dp))
                    InkButton(stringResource(R.string.pair_short), onClick = onPair)
                }
            }

            if (settings.paired) {
                SectionTitle(stringResource(R.string.set_hum), stringResource(R.string.set_hum_sub))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.set_hum_switch), style = t.body, color = p.ink, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.hum,
                        onCheckedChange = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else c.write { this.settings.setHum(on) }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = p.magnetGreen),
                    )
                }
                if (settings.hum) KeepAwake()

                SectionTitle(stringResource(R.string.set_parcel), stringResource(R.string.set_parcel_sub))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkButton(stringResource(R.string.parcel_send)) {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                val bytes = Parcel.pack(c.db.dao(), c.settings.now(), c.settings.deviceId())
                                File(context.cacheDir, "parcels").apply { mkdirs() }.listFiles()?.forEach { it.delete() }
                                File(context.cacheDir, "parcels/fridge-${LocalDate.now()}.fridge").apply { writeBytes(bytes) }
                            }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, null))
                        }
                    }
                    QuietButton(stringResource(R.string.parcel_open), color = p.blueInk, onClick = { openParcel.launch(arrayOf("*/*")) })
                }
                parcelNote?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = t.body, color = p.magnetGreen)
                }
            }

            SectionTitle(stringResource(R.string.language))
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to R.string.lang_system, "en" to R.string.lang_en, "ru" to R.string.lang_ru).forEach { (tag, label) ->
                    val on = current == tag || (tag.isNotEmpty() && current.startsWith(tag))
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) p.blueInk else p.enamel)
                            .clickable { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(stringResource(label), style = t.label, color = if (on) Color.White else p.ink) }
                }
            }

            SectionTitle(stringResource(R.string.community), stringResource(R.string.community_text))
            CommunityLinks()

            SectionTitle(stringResource(R.string.about))
            Text(stringResource(R.string.about_text), style = t.body, color = p.muted)
            Spacer(Modifier.height(6.dp))
            Text("Fridge ${BuildConfig.VERSION_NAME}", style = t.label, color = p.faint)
            Spacer(Modifier.height(30.dp))
        }
    }

    if (forgetting) {
        AlertDialog(
            onDismissRequest = { forgetting = false },
            title = { Text(stringResource(R.string.forget_title)) },
            text = { Text(stringResource(R.string.forget_text)) },
            confirmButton = { TextButton(onClick = { forgetting = false; c.write { this.settings.unpair() } }) { Text(stringResource(R.string.set_forget), color = p.magnetRed) } },
            dismissButton = { TextButton(onClick = { forgetting = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = p.enamel,
        )
    }
}
