package app.fridge.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import app.fridge.ui.art.Art
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fridge.R
import app.fridge.ui.art.magnet
import app.fridge.ui.theme.LocalPalette
import app.fridge.ui.theme.LocalType

/** The magnet colours, in the order notes pick them by their id. */
@Composable
fun magnetColors(): List<Color> {
    val p = LocalPalette.current
    return listOf(p.magnetRed, p.magnetBlue, p.magnetYellow, p.magnetGreen, p.magnetOrange, p.magnetPurple)
}

/** A round magnet on its own, for rows of them. */
@Composable
fun Magnet(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { magnet(center, this.size.minDimension / 2.3f, color) }
}

/**
 * A piece of paper held to the door: tilted a little, with a soft shadow, and a magnet pinning its top.
 * [magnet] null: tape-free paper (inside the fridge, in sheets).
 */
@Composable
fun Paper(
    color: Color,
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
    magnet: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.rotate(tilt)) {
        Column(
            Modifier
                .padding(top = if (magnet != null) 8.dp else 0.dp)
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(2.dp))
                .background(color, RoundedCornerShape(2.dp))
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
            content = content,
        )
        if (magnet != null) Magnet(magnet, 26.dp, Modifier.align(Alignment.TopCenter))
    }
}

/** A word magnet, the white strips of fridge poetry: a request said without saying it. */
@Composable
fun WordMagnet(text: String, modifier: Modifier = Modifier, tilt: Float = 0f, ink: Color? = null, big: Boolean = false, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Box(
        modifier
            .rotate(tilt)
            .shadow(2.dp, RoundedCornerShape(1.dp))
            .background(Color.White, RoundedCornerShape(1.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = if (big) 12.dp else 8.dp, vertical = if (big) 6.dp else 3.dp),
    ) {
        Text(text, style = if (big) LocalType.current.body else LocalType.current.label, color = ink ?: p.ink)
    }
}

/** A pill of ink, the app's one kind of button. */
@Composable
fun InkButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) color ?: p.blueInk else p.enamelShade)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LocalType.current.body, color = if (enabled) Color.White else p.faint)
    }
}

/** A quiet text button, for "not now" and the like. */
@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, color: Color? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = LocalType.current.body, color = color ?: p.muted)
    }
}

/** Handwriting straight onto paper: a bare field in the hand font, lined like a notepad when [lined]. */
@Composable
fun HandField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    ink: Color? = null,
    style: TextStyle = LocalType.current.hand,
    singleLine: Boolean = false,
    lined: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    val color = ink ?: p.blueInk
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(color),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.let { m ->
            if (!lined) m else m.drawBehind {
                val step = style.lineHeight.toPx()
                var y = step + 4f
                while (y < size.height + 1) {
                    drawLine(p.paperLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                    y += step
                }
            }
        },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = p.faint)
                inner()
            }
        },
    )
}

/**
 * A sheet that slides up over the door, enamel-coloured, for writing something to hang. Drawn in the
 * same window as the door (not a dialog), so it sits under the same light — and in the screen renders.
 */
@Composable
fun FridgeSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    BackHandler(onBack = onDismiss)
    val shown = remember { MutableTransitionState(Art.still).apply { targetState = true } }
    val quiet = remember { MutableInteractionSource() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tall = maxHeight * 0.92f
        AnimatedVisibility(shown, enter = fadeIn(tween(200)), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(quiet, null, onClick = onDismiss))
        }
        AnimatedVisibility(
            shown, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260)) { it }, exit = slideOutVertically { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = tall)
                    .imePadding()
                    .shadow(16.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(p.enamel, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .clickable(quiet, null) {}
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 16.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).background(p.seam, RoundedCornerShape(2.dp)))
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun SheetTitle(title: String, sub: String? = null) {
    val p = LocalPalette.current
    Text(title, style = LocalType.current.title, color = p.ink)
    if (sub != null) {
        Spacer(Modifier.height(4.dp))
        Text(sub, style = LocalType.current.body, color = p.muted)
    }
    Spacer(Modifier.height(16.dp))
}

/** A quiet top bar for the screens away from the door: back, and a title. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, tint: Color? = null) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = tint ?: p.muted) }
        Text(title, style = LocalType.current.title, color = tint ?: p.ink, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun SectionTitle(title: String, sub: String? = null) {
    val p = LocalPalette.current
    Spacer(Modifier.height(22.dp))
    Text(title.uppercase(), style = LocalType.current.label, color = p.muted)
    if (sub != null) {
        Spacer(Modifier.height(2.dp))
        Text(sub, style = LocalType.current.body, color = p.faint)
    }
    Spacer(Modifier.height(8.dp))
}
