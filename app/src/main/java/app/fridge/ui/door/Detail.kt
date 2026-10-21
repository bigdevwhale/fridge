package app.fridge.ui.door

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.container
import app.fridge.data.Item
import app.fridge.data.Kind
import app.fridge.domain.Strokes
import app.fridge.ui.art.envelope
import app.fridge.ui.art.seal
import app.fridge.ui.components.FridgeSheet
import app.fridge.ui.components.InkButton
import app.fridge.ui.components.Paper
import app.fridge.ui.components.QuietButton
import app.fridge.ui.components.WordMagnet
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType

/** One thing taken off the door to look at closely, with what can be done with it. */
@Composable
fun OpenSheet(id: String, state: DoorState, onClose: () -> Unit, onPair: () -> Unit) {
    val c = LocalContext.current.container
    val b = state.board
    val item = (b.papers + b.archive + b.asks + b.thanks + b.trips).firstOrNull { it.id == id }
    if (item == null) {
        LaunchedEffect(id) { onClose() }
        return
    }
    LaunchedEffect(id) { if (item.fresh) c.door.seen(id) }
    FridgeSheet(onClose) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (item.type) {
                Kind.ENVELOPE -> if (item.state == 0) SealedEnvelope(item, state, onPair) else Letter(item, state, onClose)
                Kind.WORRY -> Worry(item, state, onClose)
                Kind.ASK -> AskDetail(item, state, onClose)
                Kind.THANKS -> Small(item, state, onClose)
                Kind.TRIP -> Trip(item, onClose)
                else -> Note(item, state, onClose)
            }
        }
    }
}

@Composable
private fun Note(item: Item, state: DoorState, onClose: () -> Unit) {
    val p = LocalPalette.current
    val c = LocalContext.current.container
    val b = state.board
    val mine = item.author == b.me
    Paper(if (item.type == Kind.DRAWING) p.paper else noteColor(p, item.id), magnet = p.magnetRed) {
        if (item.type == Kind.DRAWING) {
            Drawing(Strokes.decode(item.extra), Modifier.fillMaxWidth().aspectRatio(0.8f))
        } else {
            Text(item.text, style = LocalType.current.handBig, color = inkOf(b, item.author))
        }
        Sign(item, state)
    }
    Spacer(Modifier.height(8.dp))
    Text(dayText(item.date.toEpochDay()), style = LocalType.current.label, color = p.faint)
    Spacer(Modifier.height(14.dp))
    Actions {
        if (!mine && item.type == Kind.NOTE) {
            InkButton(stringResource(if (item.state == 1) R.string.heart_off else R.string.heart_on), color = p.magnetRed) {
                c.write { door.heart(item.id, item.state != 1) }
                onClose()
            }
        }
        Keep(item, mine, onClose)
    }
}

/** Off the door, back on it, or gone: the same three choices for every paper. */
@Composable
private fun Keep(item: Item, mine: Boolean, onClose: () -> Unit) {
    val c = LocalContext.current.container
    if (item.archived) {
        QuietButton(stringResource(R.string.hang_again), onClick = { c.write { door.edit(item.id) { it.copy(archived = false) } }; onClose() })
    } else {
        QuietButton(stringResource(R.string.take_off), onClick = { c.write { door.archive(item.id) }; onClose() })
    }
    if (mine) QuietButton(stringResource(R.string.throw_away), color = LocalPalette.current.magnetRed, onClick = { c.write { door.remove(item.id) }; onClose() })
}

@Composable
private fun Actions(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

/**
 * A face-down worry. Its author sees what they wrote; the other one sees only that there is
 * something, and turns it over when they can really listen.
 */
@Composable
private fun Worry(item: Item, state: DoorState, onClose: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val c = LocalContext.current.container
    val b = state.board
    val mine = item.author == b.me
    val them = nameOf(b, b.partner)
    when {
        item.state == 1 -> {
            Text(stringResource(R.string.worry_turned), style = t.label, color = p.faint)
            Spacer(Modifier.height(6.dp))
            Paper(p.paper, magnet = p.magnetBlue) {
                Text(item.text, style = t.handBig, color = inkOf(b, item.author))
                Sign(item, state)
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.worry_talk), style = t.body, color = p.muted)
            Spacer(Modifier.height(12.dp))
            Actions {
                InkButton(stringResource(R.string.worry_talked)) { c.write { door.archive(item.id) }; onClose() }
                if (mine) QuietButton(stringResource(R.string.throw_away), color = p.magnetRed, onClick = { c.write { door.remove(item.id) }; onClose() })
            }
        }
        mine -> {
            Paper(Color(0xFFE9E3D6), magnet = p.magnetBlue) {
                Text(item.text, style = t.handBig, color = inkOf(b, item.author))
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(if (b.paired) R.string.worry_waits else R.string.worry_waits_alone, them), style = t.body, color = p.muted)
            Spacer(Modifier.height(12.dp))
            Actions {
                QuietButton(stringResource(R.string.take_off), onClick = { c.write { door.archive(item.id) }; onClose() })
                QuietButton(stringResource(R.string.throw_away), color = p.magnetRed, onClick = { c.write { door.remove(item.id) }; onClose() })
            }
        }
        else -> {
            Text(stringResource(R.string.worry_wants, nameOf(b, item.author)), style = t.title, color = p.ink)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.worry_wants_sub), style = t.body, color = p.muted)
            Spacer(Modifier.height(18.dp))
            Actions {
                InkButton(stringResource(R.string.worry_ready)) { c.write { door.turnOver(item.id) } }
                QuietButton(stringResource(R.string.worry_not_now), onClick = onClose)
            }
        }
    }
}

/**
 * A sealed confession. It opens only with both hands on the seal at once — each on their own phone,
 * the two phones together at home. Nobody opens it alone, not even the one who wrote it.
 */
@Composable
private fun SealedEnvelope(item: Item, state: DoorState, onPair: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val c = LocalContext.current.container
    val haptic = LocalHapticFeedback.current
    val b = state.board
    val together = state.near.together
    var holding by remember { mutableStateOf(false) }
    val theirs = item.id in state.near.theirHolds
    val progress = remember { Animatable(0f) }

    LaunchedEffect(holding) { c.hum.hold(item.id, holding) }
    DisposableEffect(item.id) { onDispose { c.hum.hold(item.id, false) } }
    LaunchedEffect(holding && theirs) {
        if (holding && theirs) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            progress.animateTo(1f, tween(1600, easing = LinearEasing))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            c.door.openEnvelope(item.id)
        } else {
            progress.animateTo(0f, tween(250))
        }
    }

    Text(stringResource(R.string.envelope_from, nameOf(b, item.author)), style = t.title, color = inkOf(b, item.author))
    Spacer(Modifier.height(14.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .pointerInput(together) {
                if (!together) return@pointerInput
                detectTapGestures(onPress = {
                    holding = true
                    tryAwaitRelease()
                    holding = false
                })
            },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.55f)) {
            envelope(p, open = false)
            val sealAt = Offset(size.width / 2, size.height * 0.55f)
            val r = size.height * 0.16f
            if (holding || theirs) drawCircle(p.lamp.copy(alpha = 0.55f), r * 1.9f, sealAt)
            seal(sealAt, r, p)
            if (progress.value > 0f) {
                drawArc(
                    lerp(p.wax, Color.White, 0.4f), -90f, 360f * progress.value, false,
                    topLeft = sealAt - Offset(r * 1.5f, r * 1.5f), size = Size(r * 3f, r * 3f),
                    style = Stroke(r * 0.18f, cap = StrokeCap.Round),
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    val them = nameOf(b, if (item.author == b.me) b.partner else item.author)
    val line = when {
        !b.paired -> stringResource(R.string.envelope_unpaired)
        !together -> stringResource(R.string.envelope_apart)
        holding && theirs -> stringResource(R.string.envelope_opening)
        holding -> stringResource(R.string.envelope_you_hold, them)
        theirs -> stringResource(R.string.envelope_they_hold, them)
        else -> stringResource(R.string.envelope_hold, them)
    }
    Text(line, style = t.body, color = if (theirs && !holding) p.magnetRed else p.muted)
    if (!b.paired) {
        Spacer(Modifier.height(12.dp))
        InkButton(stringResource(R.string.pair_short), onClick = onPair)
    }
    if (item.author == b.me) {
        Spacer(Modifier.height(14.dp))
        Actions { QuietButton(stringResource(R.string.throw_away), color = p.magnetRed, onClick = { c.write { door.remove(item.id) } }) }
    }
}

@Composable
private fun Letter(item: Item, state: DoorState, onClose: () -> Unit) {
    val p = LocalPalette.current
    val b = state.board
    Text(stringResource(R.string.envelope_opened_on, dayText(if (item.day != 0L) item.day else item.date.toEpochDay())), style = LocalType.current.label, color = p.faint)
    Spacer(Modifier.height(8.dp))
    Paper(lerp(p.paper, p.kraft, 0.15f)) {
        Text(item.text, style = LocalType.current.handBig, color = inkOf(b, item.author))
        Sign(item, state)
    }
    Spacer(Modifier.height(14.dp))
    Actions { Keep(item, item.author == b.me, onClose) }
}

@Composable
private fun AskDetail(item: Item, state: DoorState, onClose: () -> Unit) {
    val p = LocalPalette.current
    val c = LocalContext.current.container
    val b = state.board
    val mine = item.author == b.me
    Text(
        stringResource(if (mine) R.string.ask_mine else R.string.ask_theirs, nameOf(b, item.author)),
        style = LocalType.current.body, color = p.muted,
    )
    Spacer(Modifier.height(14.dp))
    WordMagnet(askText(item.text), big = true, ink = inkOf(b, item.author))
    Spacer(Modifier.height(20.dp))
    Actions {
        if (item.archived) {
            Text(stringResource(if (item.state == 1) R.string.ask_answered else R.string.ask_taken_off), style = LocalType.current.label, color = p.faint)
        } else if (mine) {
            QuietButton(stringResource(R.string.take_off), onClick = { c.write { door.archive(item.id) }; onClose() })
        } else {
            InkButton(stringResource(R.string.ask_coming), color = p.magnetRed) { c.write { door.answer(item.id) }; onClose() }
        }
    }
}

@Composable
private fun Small(item: Item, state: DoorState, onClose: () -> Unit) {
    val p = LocalPalette.current
    val c = LocalContext.current.container
    val b = state.board
    Text(stringResource(R.string.thanks_from, nameOf(b, item.author), dayText(item.date.toEpochDay())), style = LocalType.current.label, color = p.muted)
    Spacer(Modifier.height(8.dp))
    Paper(p.pink, magnet = if (b.isBlue(item.author)) p.magnetBlue else p.magnetRed) {
        Text(item.text, style = LocalType.current.handBig, color = inkOf(b, item.author))
    }
    if (item.author == b.me) {
        Spacer(Modifier.height(14.dp))
        Actions { QuietButton(stringResource(R.string.throw_away), color = p.magnetRed, onClick = { c.write { door.remove(item.id) }; onClose() }) }
    }
}

@Composable
private fun Trip(item: Item, onClose: () -> Unit) {
    val p = LocalPalette.current
    val c = LocalContext.current.container
    Text(item.text, style = LocalType.current.plate, color = p.ink)
    if (item.day != 0L) Text(dayText(item.day), style = LocalType.current.label, color = p.muted)
    Spacer(Modifier.height(12.dp))
    item.blob?.let { bytes ->
        val bmp = remember(bytes) { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(12.dp))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        QuietButton(stringResource(R.string.throw_away), color = p.magnetRed, onClick = { c.write { door.remove(item.id) }; onClose() })
    }
}
