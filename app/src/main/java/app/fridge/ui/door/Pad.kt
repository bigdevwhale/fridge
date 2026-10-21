package app.fridge.ui.door

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.container
import app.fridge.data.Item
import app.fridge.domain.Chores
import app.fridge.ui.components.HandField
import app.fridge.ui.components.Magnet
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType
import java.time.LocalDate

/**
 * The shopping pad: a lined notepad under a magnet. Each of you writes in your own ink; whoever
 * crosses a line out crosses it with theirs. Staples go back to their jar when cleared, one tap brings
 * them back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroceryPad(state: DoorState) {
    val p = LocalPalette.current
    val t = LocalType.current
    val c = LocalContext.current.container
    val b = state.board
    var typing by remember { mutableStateOf("") }
    Box(Modifier.rotate(-1.2f)) {
        Column(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(2.dp))
                .background(p.paper, RoundedCornerShape(2.dp))
                // The torn-off top of a notepad.
                .drawBehind { drawRect(p.paperLine.copy(alpha = 0.35f), size = size.copy(height = 6.dp.toPx())) }
                .padding(start = 16.dp, end = 12.dp, top = 22.dp, bottom = 12.dp),
        ) {
            Text(stringResource(R.string.pad_title), style = t.handBig, color = p.ink)
            Spacer(Modifier.height(4.dp))
            b.groceries.forEach { g -> GroceryLine(g, state) }
            HandField(
                value = typing,
                onValueChange = { typing = it.take(60) },
                placeholder = stringResource(R.string.pad_add),
                ink = inkOf(b, b.me),
                singleLine = true,
                lined = true,
                onDone = {
                    val text = typing
                    typing = ""
                    c.write { door.addGrocery(text) }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
            if (b.groceries.any { it.done }) {
                Text(
                    stringResource(R.string.pad_clear), style = t.label, color = p.blueInk,
                    modifier = Modifier.align(Alignment.End).clickable { c.write { door.clearCrossed() } }.padding(6.dp),
                )
            }
            if (b.jars.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.pad_jars), style = t.label, color = p.faint)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.jars.forEach { j ->
                        Text(
                            "+ " + j.text, style = t.handSmall, color = p.muted,
                            modifier = Modifier.background(p.enamel, RoundedCornerShape(10.dp)).clickable { c.write { door.addGrocery(j.text) } }.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Magnet(p.magnetYellow, 28.dp, Modifier.align(Alignment.TopCenter))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroceryLine(g: Item, state: DoorState) {
    val p = LocalPalette.current
    val c = LocalContext.current.container
    val b = state.board
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { c.write { door.cross(g.id, !g.done) } }, onLongClick = { menu = true })
                .drawBehind {
                    val y = size.height - 2f
                    drawLine(p.paperLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                }
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var width by remember { mutableFloatStateOf(0f) }
            Box(Modifier.weight(1f)) {
                Text(
                    g.text, style = LocalType.current.hand, color = inkOf(b, g.author).copy(alpha = if (g.done) 0.5f else 1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, onTextLayout = { width = it.size.width.toFloat() },
                )
                if (g.done) {
                    // Crossed out in the ink of whoever crossed it.
                    val strike = inkOf(b, g.doneBy.ifEmpty { g.author })
                    Box(Modifier.matchParentSize().drawBehind {
                        val y = size.height * 0.55f
                        drawLine(strike, Offset(-4f, y + 3f), Offset(width + 6f, y - 2f), strokeWidth = 2.5.dp.toPx())
                    })
                }
            }
            if (g.state == 1) Text("•", style = LocalType.current.label, color = p.faint, modifier = Modifier.padding(start = 6.dp))
            if (g.isNew(b)) NewDot(Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (g.state == 1) R.string.pad_unstaple else R.string.pad_staple)) },
                onClick = { menu = false; c.write { door.staple(g.id, g.state != 1) } },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = { menu = false; c.write { door.remove(g.id) } })
        }
    }
}

/**
 * Whose turn: chores on a card, each with the magnet of whoever is next. Doing one passes the turn;
 * the month's tally sits at the bottom, as a plain fact.
 */
@Composable
fun ChoreCard(state: DoorState, open: (Sheet) -> Unit) {
    val p = LocalPalette.current
    val t = LocalType.current
    val c = LocalContext.current.container
    val b = state.board
    var typing by remember { mutableStateOf("") }
    Box(Modifier.rotate(0.8f)) {
        Column(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(2.dp))
                .background(p.sky, RoundedCornerShape(2.dp))
                .padding(start = 16.dp, end = 14.dp, top = 20.dp, bottom = 12.dp),
        ) {
            Text(stringResource(R.string.chores_title), style = t.handBig, color = p.ink)
            Spacer(Modifier.height(4.dp))
            b.chores.forEach { ch ->
                val turn = ch.doneBy.ifEmpty { b.me }
                Row(
                    Modifier.fillMaxWidth().clickable { open(Sheet.Chore(ch.id)) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ch.text, style = t.hand, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (ch.state == 1) Text(stringResource(R.string.chore_swap_mark), style = t.label, color = p.magnetRed, modifier = Modifier.padding(end = 6.dp))
                    Text(nameOf(b, turn), style = t.handSmall, color = inkOf(b, turn), maxLines = 1, modifier = Modifier.padding(end = 6.dp))
                    Magnet(if (b.isBlue(turn)) p.magnetBlue else p.magnetRed, 26.dp)
                }
            }
            HandField(
                value = typing,
                onValueChange = { typing = it.take(40) },
                placeholder = stringResource(R.string.chores_add),
                ink = p.ink,
                singleLine = true,
                onDone = {
                    val text = typing.trim()
                    typing = ""
                    if (text.isNotEmpty()) c.write { door.chore(text, b.me) }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            val today = LocalDate.ofEpochDay(b.today)
            val tally = Chores.balance(b.choreLogs, today.withDayOfMonth(1).toEpochDay(), b.today)
            if (tally.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                val people = listOfNotNull(b.me, b.partner.takeIf { b.paired }).map { it to nameOf(b, it) }
                val line = people.joinToString(" · ") { (who, name) -> "$name ${tally[who] ?: 0}" }
                Text(stringResource(R.string.chores_tally, line), style = t.label, color = p.muted)
            }
        }
        Magnet(p.magnetGreen, 28.dp, Modifier.align(Alignment.TopCenter))
    }
}

/** A plain magnet with a word on it: behind it, each of your wishes. */
@Composable
fun Hideout(state: DoorState, open: (Sheet) -> Unit) {
    val p = LocalPalette.current
    val b = state.board
    val theirs = b.wishes.count { it.author != b.me }
    // On the left: the corner on the right belongs to the round "+" magnet.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            Modifier
                .rotate(-3f)
                .shadow(3.dp, RoundedCornerShape(16.dp))
                .background(p.magnetPurple, RoundedCornerShape(16.dp))
                .clickable { open(Sheet.Wishes) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.hideout), style = LocalType.current.plate.copy(fontSize = LocalType.current.plate.fontSize * 0.8f), color = Color.White)
            if (theirs > 0) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(22.dp).background(Color.White.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(theirs.toString(), style = LocalType.current.label, color = Color.White)
                }
            }
        }
    }
}
