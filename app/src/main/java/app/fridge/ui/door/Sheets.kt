package app.fridge.ui.door

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.container
import app.fridge.domain.Ask
import app.fridge.domain.Stroke
import app.fridge.domain.Strokes
import app.fridge.domain.Weather
import app.fridge.ui.art.magnet
import app.fridge.ui.art.weather
import app.fridge.ui.components.FridgeSheet
import app.fridge.ui.components.HandField
import app.fridge.ui.components.InkButton
import app.fridge.ui.components.Paper
import app.fridge.ui.components.QuietButton
import app.fridge.ui.components.SheetTitle
import app.fridge.ui.components.WordMagnet
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun Sheets(sheet: Sheet, state: DoorState, onClose: () -> Unit, onOpen: (Sheet) -> Unit, onPair: () -> Unit) {
    when (sheet) {
        Sheet.Add -> AddSheet(onClose, onOpen)
        Sheet.Weather -> WeatherSheet(onClose)
        Sheet.Asks -> AsksSheet(onClose)
        is Sheet.Write -> WriteSheet(sheet.what, state, onClose)
        Sheet.Draw -> DrawSheet(onClose)
        Sheet.Trip -> TripSheet(onClose)
        Sheet.Wishes -> WishesSheet(state, onClose)
        is Sheet.Open -> OpenSheet(sheet.id, state, onClose, onPair)
        is Sheet.Chore -> ChoreSheet(sheet.id, state, onClose)
    }
}

/** "What would you like to hang?" — every kind of thing the door can hold. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSheet(onClose: () -> Unit, onOpen: (Sheet) -> Unit) {
    val p = LocalPalette.current
    FridgeSheet(onClose) {
        SheetTitle(stringResource(R.string.add_title))
        val options = listOf(
            Triple(R.string.add_note, R.string.add_note_sub, Sheet.Write(Writing.NOTE)),
            Triple(R.string.add_morning, R.string.add_morning_sub, Sheet.Write(Writing.MORNING)),
            Triple(R.string.add_worry, R.string.add_worry_sub, Sheet.Write(Writing.WORRY)),
            Triple(R.string.add_envelope, R.string.add_envelope_sub, Sheet.Write(Writing.ENVELOPE)),
            Triple(R.string.add_ask, R.string.add_ask_sub, Sheet.Asks),
            Triple(R.string.add_weather, R.string.add_weather_sub, Sheet.Weather),
            Triple(R.string.add_thanks, R.string.add_thanks_sub, Sheet.Write(Writing.THANKS)),
            Triple(R.string.add_drawing, R.string.add_drawing_sub, Sheet.Draw),
            Triple(R.string.add_trip, R.string.add_trip_sub, Sheet.Trip),
            Triple(R.string.add_wish, R.string.add_wish_sub, Sheet.Wishes),
        )
        val colors = listOf(p.yellow, p.pink, Color9E, p.kraft, p.paper, p.sky, p.pink, p.paper, p.mint, p.yellow)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
                options.forEachIndexed { i, (title, sub, target) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 92.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors[i])
                            .clickable { onOpen(target) }
                            .padding(12.dp),
                    ) {
                        Text(stringResource(title), style = LocalType.current.hand, color = p.ink)
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(sub), style = LocalType.current.label, color = p.muted)
                    }
                }
            }
        }
    }
}

/** The back of a face-down paper, for its tile. */
private val Color9E = androidx.compose.ui.graphics.Color(0xFFE9E3D6)

/** Today's weather, chosen from six round magnets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeatherSheet(onClose: () -> Unit) {
    val p = LocalPalette.current
    val c = LocalContext.current.container
    FridgeSheet(onClose) {
        SheetTitle(stringResource(R.string.weather_title), stringResource(R.string.weather_sub))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 3) {
            Weather.entries.forEach { w ->
                Column(
                    Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).clickable { c.write { door.weather(w) }; onClose() }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.size(72.dp)) {
                        val r = size.minDimension / 2.2f
                        magnet(center, r, androidx.compose.ui.graphics.Color(0xFFFDFBF6))
                        val box = r * 1.45f
                        weather(w, center - Offset(box / 2, box / 2), box, p)
                    }
                    Text(weatherName(w), style = LocalType.current.label, color = p.ink)
                }
            }
        }
    }
}

/** Word magnets for asking without having to say it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AsksSheet(onClose: () -> Unit) {
    val c = LocalContext.current.container
    FridgeSheet(onClose) {
        SheetTitle(stringResource(R.string.asks_title), stringResource(R.string.asks_sub))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Ask.entries.forEachIndexed { i, a ->
                WordMagnet(askText(a.name), tilt = if (i % 2 == 0) -2f else 2f, big = true, onClick = { c.write { door.ask(a) }; onClose() })
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Writing by hand on a piece of paper: a note, a morning note, a worry, a confession, a thank-you. */
@Composable
private fun WriteSheet(what: Writing, state: DoorState, onClose: () -> Unit) {
    val p = LocalPalette.current
    val c = LocalContext.current.container
    var text by remember { mutableStateOf("") }
    var worryToday by remember { mutableStateOf(false) }
    if (what == Writing.WORRY) LaunchedEffect(Unit) { worryToday = !c.door.canHangWorry() }
    val (title, sub, hint) = when (what) {
        Writing.NOTE -> Triple(R.string.add_note, R.string.write_note_sub, R.string.write_note_hint)
        Writing.MORNING -> Triple(R.string.add_morning, R.string.write_morning_sub, R.string.write_morning_hint)
        Writing.WORRY -> Triple(R.string.add_worry, R.string.write_worry_sub, R.string.write_worry_hint)
        Writing.ENVELOPE -> Triple(R.string.add_envelope, R.string.write_envelope_sub, R.string.write_envelope_hint)
        Writing.THANKS -> Triple(R.string.add_thanks, R.string.write_thanks_sub, R.string.write_thanks_hint)
    }
    val limit = if (what == Writing.THANKS) 90 else 600
    FridgeSheet(onClose) {
        SheetTitle(stringResource(title), stringResource(sub))
        if (worryToday) {
            Text(stringResource(R.string.write_worry_once), style = LocalType.current.body, color = p.magnetRed)
            Spacer(Modifier.height(12.dp))
        }
        val paper = when (what) {
            Writing.WORRY -> p.paper
            Writing.ENVELOPE -> androidx.compose.ui.graphics.lerp(p.paper, p.kraft, 0.15f)
            Writing.THANKS -> p.pink
            else -> p.yellow
        }
        Paper(paper, magnet = p.magnetRed) {
            HandField(
                value = text, onValueChange = { text = it.take(limit) },
                placeholder = stringResource(hint),
                ink = inkOf(state.board, state.board.me),
                modifier = Modifier.fillMaxWidth().heightIn(min = if (what == Writing.THANKS) 60.dp else 140.dp),
                lined = true,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            QuietButton(stringResource(R.string.cancel), onClick = onClose)
            Spacer(Modifier.width(8.dp))
            InkButton(stringResource(if (what == Writing.ENVELOPE) R.string.seal else R.string.hang), enabled = text.isNotBlank() && !worryToday) {
                val t = text
                c.write {
                    when (what) {
                        Writing.NOTE -> door.note(t, forMorning = false)
                        Writing.MORNING -> door.note(t, forMorning = true)
                        Writing.WORRY -> door.worry(t)
                        Writing.ENVELOPE -> door.envelope(t)
                        Writing.THANKS -> door.thanks(t)
                    }
                }
                onClose()
            }
        }
    }
}

/** A drawing on squared paper, finger for a crayon. */
@Composable
private fun DrawSheet(onClose: () -> Unit) {
    val p = LocalPalette.current
    val c = LocalContext.current.container
    val colors = crayons()
    val lines = remember { mutableStateListOf<Stroke>() }
    var crayon by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    FridgeSheet(onClose) {
        SheetTitle(stringResource(R.string.add_drawing), stringResource(R.string.draw_sub))
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .align(Alignment.CenterHorizontally)
                .aspectRatio(0.8f)
                .background(p.paper)
                .border(1.dp, p.seam)
                .drawBehind { squares(p.paperLine.copy(alpha = 0.6f)) }
                .pointerInput(crayon) {
                    detectDragGestures(
                        onDragStart = { o -> current = listOf(o.x / size.width to o.y / size.height) },
                        onDrag = { change, _ ->
                            change.consume()
                            current = current + (change.position.x / size.width to change.position.y / size.height)
                        },
                        onDragEnd = {
                            if (current.isNotEmpty()) lines += Stroke(crayon, current)
                            current = emptyList()
                        },
                    )
                },
        ) {
            Drawing(lines.toList() + listOfNotNull(current.takeIf { it.isNotEmpty() }?.let { Stroke(crayon, it) }), Modifier.matchParentSize())
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            colors.forEachIndexed { i, col ->
                Box(
                    Modifier.padding(horizontal = 5.dp).size(if (i == crayon) 34.dp else 28.dp).clip(CircleShape).background(col)
                        .border(if (i == crayon) 3.dp else 0.dp, p.enamel, CircleShape).clickable { crayon = i },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (lines.isNotEmpty()) QuietButton(stringResource(R.string.undo), onClick = { lines.removeAt(lines.lastIndex) })
            Spacer(Modifier.weight(1f))
            QuietButton(stringResource(R.string.cancel), onClick = onClose)
            InkButton(stringResource(R.string.hang), enabled = lines.isNotEmpty()) {
                val s = Strokes.encode(lines.toList())
                c.write { door.drawing(s) }
                onClose()
            }
        }
    }
}

/** A souvenir magnet: where you were, when, and a photo for its back if you like. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripSheet(onClose: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val c = context.container
    var place by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var picking by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) photo = runCatching { shrink(context, uri) }.getOrNull()
    }
    FridgeSheet(onClose) {
        SheetTitle(stringResource(R.string.add_trip), stringResource(R.string.trip_sub))
        Paper(p.paper, magnet = p.magnetOrange) {
            HandField(place, { place = it.take(30) }, stringResource(R.string.trip_place), singleLine = true, ink = p.ink, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(dayText(day), style = LocalType.current.hand, color = p.blueInk, modifier = Modifier.clickable { picking = true })
        }
        Spacer(Modifier.height(12.dp))
        photo?.let { bytes ->
            val bmp = remember(bytes) { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            if (bmp != null) Image(bmp, null, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(8.dp))
        }
        QuietButton(
            stringResource(if (photo == null) R.string.trip_photo else R.string.trip_photo_change), color = p.blueInk,
            onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            QuietButton(stringResource(R.string.cancel), onClick = onClose)
            InkButton(stringResource(R.string.hang), enabled = place.isNotBlank()) {
                val (pl, d, ph) = Triple(place, day, photo)
                c.write { door.trip(pl, d, ph) }
                onClose()
            }
        }
    }
    if (picking) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    picking = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(picker) }
    }
}

/** A photo for a magnet's back: small enough to travel (640 px, JPEG). */
private fun shrink(context: android.content.Context, uri: Uri): ByteArray {
    val src = ImageDecoder.createSource(context.contentResolver, uri)
    val bmp = ImageDecoder.decodeBitmap(src) { d, info, _ ->
        val s = info.size
        val scale = 640f / maxOf(s.width, s.height)
        if (scale < 1f) d.setTargetSize((s.width * scale).toInt(), (s.height * scale).toInt())
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 78, it) }.toByteArray()
}

/**
 * The hideout behind the purple magnet: your wishes, and theirs. You can mark one of theirs as
 * taken care of — they never see that mark, so a present stays a surprise.
 */
@Composable
private fun WishesSheet(state: DoorState, onClose: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val c = LocalContext.current.container
    val b = state.board
    var typing by remember { mutableStateOf("") }
    FridgeSheet(onClose) {
        SheetTitle(stringResource(R.string.hideout), stringResource(R.string.wishes_sub))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (b.paired) {
                Text(stringResource(R.string.wishes_theirs, nameOf(b, b.partner)), style = t.label, color = p.muted)
                val theirs = b.wishes.filter { it.author != b.me }
                if (theirs.isEmpty()) Text(stringResource(R.string.wishes_theirs_empty), style = t.hand, color = p.faint, modifier = Modifier.padding(vertical = 6.dp))
                theirs.forEach { w ->
                    Row(Modifier.fillMaxWidth().clickable { c.write { door.gotIt(w.id, !w.done) } }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(w.done, onCheckedChange = { on -> c.write { door.gotIt(w.id, on) } }, colors = CheckboxDefaults.colors(checkedColor = p.magnetPurple))
                        Text(w.text, style = t.hand, color = inkOf(b, w.author), modifier = Modifier.weight(1f))
                        if (w.done) Text(stringResource(R.string.wishes_got), style = t.label, color = p.magnetPurple)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Text(stringResource(R.string.wishes_mine), style = t.label, color = p.muted)
            b.wishes.filter { it.author == b.me }.forEach { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("· " + w.text, style = t.hand, color = inkOf(b, w.author), modifier = Modifier.weight(1f))
                    QuietButton("×", onClick = { c.write { door.remove(w.id) } })
                }
            }
            HandField(
                typing, { typing = it.take(80) }, stringResource(R.string.wishes_add), singleLine = true, ink = inkOf(b, b.me),
                onDone = {
                    val w = typing.trim()
                    typing = ""
                    if (w.isNotEmpty()) c.write { door.wish(w) }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }
}

/** One chore: done, swap, or off the card. */
@Composable
private fun ChoreSheet(id: String, state: DoorState, onClose: () -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val c = LocalContext.current.container
    val b = state.board
    val chore = b.chores.firstOrNull { it.id == id }
    if (chore == null) {
        LaunchedEffect(id) { onClose() }
        return
    }
    val turn = chore.doneBy.ifEmpty { b.me }
    val mine = turn == b.me
    FridgeSheet(onClose) {
        Text(chore.text, style = t.handBig, color = p.ink)
        Text(stringResource(R.string.chore_turn, nameOf(b, turn)), style = t.body, color = inkOf(b, turn))
        Spacer(Modifier.height(18.dp))
        if (chore.state == 1 && !mine) {
            Text(stringResource(R.string.chore_swap_asked, nameOf(b, turn)), style = t.body, color = p.ink)
            Spacer(Modifier.height(10.dp))
            Row {
                InkButton(stringResource(R.string.chore_swap_yes)) { c.write { door.swap(id, true, b.partner) }; onClose() }
                Spacer(Modifier.width(8.dp))
                QuietButton(stringResource(R.string.chore_swap_no), onClick = { c.write { door.swap(id, false, b.partner) }; onClose() })
            }
            Spacer(Modifier.height(14.dp))
        }
        InkButton(stringResource(R.string.chore_done), Modifier.fillMaxWidth(), color = p.magnetGreen) { c.write { door.choreDone(id, b.partner) }; onClose() }
        Spacer(Modifier.height(6.dp))
        if (mine && b.paired) {
            QuietButton(
                stringResource(if (chore.state == 1) R.string.chore_swap_cancel else R.string.chore_swap_ask), Modifier.fillMaxWidth(), color = p.blueInk,
                onClick = { c.write { door.askSwap(id, chore.state != 1) }; onClose() },
            )
        }
        QuietButton(stringResource(R.string.chore_remove), Modifier.fillMaxWidth(), onClick = { c.write { door.remove(id) }; onClose() })
    }
}
