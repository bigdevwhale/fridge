package app.fridge.ui.door

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.container
import app.fridge.data.FridgeSettings
import app.fridge.data.Item
import app.fridge.domain.Board
import app.fridge.sync.Near
import app.fridge.ui.art.Art
import app.fridge.ui.art.chrome
import app.fridge.ui.art.magnet
import app.fridge.ui.art.tiles
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType
import kotlinx.coroutines.delay

/** What slides up over the door. */
sealed interface Sheet {
    data object Add : Sheet
    data object Weather : Sheet
    data object Asks : Sheet
    data class Write(val what: Writing) : Sheet
    data object Draw : Sheet
    data object Trip : Sheet
    data object Wishes : Sheet
    data class Open(val id: String) : Sheet
    data class Chore(val id: String) : Sheet
}

/** The things written by hand on a piece of paper (or a magnet). */
enum class Writing { NOTE, MORNING, WORRY, ENVELOPE, THANKS }

/** The door, as everything on this screen sees it. */
class DoorState(val board: Board, val near: Near, val settings: FridgeSettings, val now: Long)

/**
 * The whole app is this door: the freezer on top (who's who, the weather, wordless asks, thanks,
 * trips), the main door below (the shopping pad, whose turn, notes), and the handle that opens the
 * fridge — the archive. Everything else slides up over it and goes away again.
 */
@Composable
fun DoorScreen(settings: FridgeSettings, onInside: () -> Unit, onSettings: () -> Unit, onPair: () -> Unit) {
    val c = LocalContext.current.container
    val items by c.db.dao().live().collectAsState(initial = emptyList())
    val near by c.hum.near.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val board = remember(items, settings, now) { Board.of(items, settings, now) }
    DoorContent(DoorState(board, near, settings, now), onInside, onSettings, onPair)
}

@Composable
fun DoorContent(state: DoorState, onInside: () -> Unit, onSettings: () -> Unit, onPair: () -> Unit, startSheet: Sheet? = null, startScroll: Int = 0) {
    val p = LocalPalette.current
    var sheet by remember { mutableStateOf(startSheet) }
    val open: (Sheet) -> Unit = { sheet = it }

    Box(Modifier.fillMaxSize().drawBehind { tiles(p, 44.dp.toPx()) }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(startScroll))) {
            Status(state, onSettings, onPair)
            Fridge(state, open, onInside)
            Spacer(Modifier.navigationBarsPadding().height(96.dp))
        }
        AddMagnet(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)) { sheet = Sheet.Add }
        sheet?.let { s -> Sheets(s, state, onClose = { sheet = null }, onOpen = open, onPair = onPair) }
    }
}

/** The strip above the fridge: is the other phone here, and the way to settings. */
@Composable
private fun Status(state: DoorState, onSettings: () -> Unit, onPair: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val s = state.settings
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lamp(on = state.near.together, humming = state.near.humming)
        Spacer(Modifier.width(10.dp))
        val name = s.partnerName.ifBlank { stringResource(R.string.them) }
        val text = when {
            state.near.together -> stringResource(R.string.status_together, name)
            s.waiting -> stringResource(R.string.status_waiting)
            !s.paired -> stringResource(R.string.status_alone)
            s.lastMet > 0 -> stringResource(R.string.status_last_met, ago(state.now - s.lastMet))
            else -> stringResource(R.string.status_paired)
        }
        Text(
            text, style = t.label, color = p.muted,
            modifier = Modifier.weight(1f).let { if (!s.paired && !s.waiting) it.clickable(onClick = onPair) else it },
        )
        if (!s.paired && !s.waiting) {
            Text(
                stringResource(R.string.pair_short), style = t.label, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(p.blueInk).clickable(onClick = onPair).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings), tint = p.muted) }
    }
}

@Composable
private fun ago(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 2 -> stringResource(R.string.ago_now)
        min < 60 -> stringResource(R.string.ago_min, min.toInt())
        min < 60 * 24 -> stringResource(R.string.ago_hours, (min / 60).toInt())
        else -> stringResource(R.string.ago_days, (min / 60 / 24).toInt())
    }
}

/** The little light on the wall: green and breathing while the two phones talk. */
@Composable
private fun Lamp(on: Boolean, humming: Boolean) {
    val p = LocalPalette.current
    val glow = if (on && !Art.still) {
        rememberInfiniteTransition(label = "lamp").animateFloat(0.45f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow").value
    } else 1f
    Canvas(Modifier.size(12.dp)) {
        val color = when {
            on -> Color(0xFF4CAF68)
            humming -> Color(0xFFE0B54A)
            else -> p.faint
        }
        if (on) drawCircle(color.copy(alpha = 0.3f * glow), size.minDimension)
        drawCircle(color, size.minDimension / 2.4f)
    }
}

/** The fridge itself: enamel body, freezer, seam, door, handle, feet. */
@Composable
private fun Fridge(state: DoorState, open: (Sheet) -> Unit, onInside: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
            .drawBehind {
                drawRect(Brush.horizontalGradient(0f to p.enamel, 0.82f to p.enamel, 1f to p.enamelShade))
            },
    ) {
        Freezer(state, open)
        // The seam between the doors.
        Box(Modifier.fillMaxWidth().height(5.dp).background(p.seam))
        MainDoor(state, open, onInside)
        Box(Modifier.fillMaxWidth().height(16.dp).background(p.enamelShade))
    }
    // Feet.
    Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(2) { Box(Modifier.size(width = 26.dp, height = 8.dp).background(p.chromeDark, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))) }
    }
}

@Composable
private fun MainDoor(state: DoorState, open: (Sheet) -> Unit, onInside: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 40.dp, top = 22.dp, bottom = 26.dp)) {
            GroceryPad(state)
            Spacer(Modifier.height(26.dp))
            ChoreCard(state, open)
            Spacer(Modifier.height(26.dp))
            Papers(state, open)
            Spacer(Modifier.height(18.dp))
            Hideout(state, open)
        }
        // The long chrome handle: pull it and the fridge opens.
        val desc = stringResource(R.string.open_fridge)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 10.dp)
                .size(width = 22.dp, height = 220.dp)
                .semantics { contentDescription = desc }
                .clip(RoundedCornerShape(11.dp))
                .clickable(onClick = onInside)
                .drawBehind {
                    chrome(Offset(size.width * 0.25f, 0f), Size(size.width * 0.5f, size.height), p)
                    drawRoundRect(p.chromeDark.copy(alpha = 0.5f), Offset(0f, 0f), Size(size.width, 10.dp.toPx()), CornerRadius(4f))
                    drawRoundRect(p.chromeDark.copy(alpha = 0.5f), Offset(0f, size.height - 10.dp.toPx()), Size(size.width, 10.dp.toPx()), CornerRadius(4f))
                },
        )
    }
}

/** The big round "+" magnet in the corner: what would you like to hang? */
@Composable
private fun AddMagnet(modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val desc = stringResource(R.string.add)
    Box(
        modifier.size(64.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            magnet(center, size.minDimension / 2.25f, p.magnetRed)
            val arm = size.minDimension * 0.16f
            drawLine(Color.White, center - Offset(arm, 0f), center + Offset(arm, 0f), strokeWidth = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(Color.White, center - Offset(0f, arm), center + Offset(0f, arm), strokeWidth = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** The ink a person writes in on this door. */
@Composable
fun inkOf(board: Board, person: String): Color = if (board.isBlue(person)) LocalPalette.current.blueInk else LocalPalette.current.redInk

/** Who it is, as a name to sign with. */
@Composable
fun nameOf(board: Board, person: String): String = board.nameOf(person, stringResource(R.string.me), stringResource(R.string.them))

/** Is it from the other one and not yet looked at? */
fun Item.isNew(board: Board) = fresh && author != board.me
