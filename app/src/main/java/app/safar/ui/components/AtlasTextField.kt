package app.safar.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.safar.ui.art.AtlasIcons
import app.safar.ui.theme.Atlas

/**
 * Notebook-style text field (`.field`): kicker label, large serif entry on an ink baseline that
 * turns rust when focused. [lockedReason] locks it (UX rule 3: no typing while moving): the
 * baseline goes dashed, a lock shows, and the reason is spelled out underneath in amber.
 */
@Composable
fun AtlasTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    lockedReason: String? = null,
    placeholder: String? = null,
    maxLength: Int? = null,
    textStyle: TextStyle = Atlas.type.displayMedium.copy(fontSize = Atlas.type.displayMedium.fontSize * 0.85f),
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Atlas.colors
    val locked = lockedReason != null
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val buffer = rememberFieldBuffer(value)
    val line = when {
        locked -> c.ink3
        focused -> c.rust
        else -> c.ink
    }
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Kicker(label)
        Row(
            Modifier.fillMaxWidth().heightIn(min = Atlas.dimens.touchTarget).padding(top = 8.dp).baseline(line, dashed = locked),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FieldInput(
                buffer, { v -> onValueChange(buffer.edit(v, maxLength)) }, label, lockedReason, placeholder, textStyle,
                KeyboardOptions(KeyboardCapitalization.Words, keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                source, Modifier.weight(1f),
            )
            FieldTrailing(locked, trailing, maxLength?.let { "${buffer.field.text.length}/$it" })
        }
        if (lockedReason != null) LockLine(lockedReason)
    }
}

/**
 * The edit buffer lives here, in Compose state, and is updated in the same frame as the keyboard's edit. The
 * caller's [value] usually comes back through an asynchronous flow; feeding that straight into the field makes
 * the IME and the field disagree and drops or repeats characters. Late copies of our own edits are ignored;
 * any other [value] (a stored value loading, a reset) replaces the buffer.
 */
@Stable
internal class FieldBuffer(initial: String) {
    var field by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        private set
    private val echoes = EditEchoes()

    /** Applies a keyboard edit (cut to [maxLength]) and returns the text to forward. */
    fun edit(v: TextFieldValue, maxLength: Int?): String {
        val next = if (maxLength != null && v.text.length > maxLength) v.copy(text = v.text.take(maxLength)) else v
        field = next
        echoes.sent(next.text)
        return next.text
    }

    fun sync(external: String) {
        if (echoes.isEcho(external) || external == field.text) return
        field = TextFieldValue(external, TextRange(external.length))
    }
}

@Composable
private fun rememberFieldBuffer(value: String): FieldBuffer {
    val buffer = remember { FieldBuffer(value) }
    LaunchedEffect(value) { buffer.sync(value) }
    return buffer
}

@Composable
private fun FieldInput(
    buffer: FieldBuffer,
    onEdit: (TextFieldValue) -> Unit,
    label: String,
    lockedReason: String?,
    placeholder: String?,
    textStyle: TextStyle,
    keyboard: KeyboardOptions,
    source: MutableInteractionSource,
    modifier: Modifier,
) {
    val locked = lockedReason != null
    BasicTextField(
        value = buffer.field,
        onValueChange = onEdit,
        modifier = modifier.padding(vertical = 10.dp).semantics {
            contentDescription = label
            if (locked) stateDescription = "Locked. $lockedReason"
        },
        enabled = !locked,
        singleLine = true,
        textStyle = textStyle.copy(color = if (locked) Atlas.colors.ink3 else Atlas.colors.ink),
        cursorBrush = SolidColor(Atlas.colors.rust),
        interactionSource = source,
        keyboardOptions = keyboard,
        decorationBox = { inner -> Placeholder(if (buffer.field.text.isEmpty()) placeholder else null, textStyle, inner) },
    )
}

@Composable
private fun Placeholder(placeholder: String?, style: TextStyle, inner: @Composable () -> Unit) {
    Box {
        if (placeholder != null) BasicText(placeholder, style = style.copy(color = Atlas.colors.ink3.copy(alpha = 0.6f)))
        inner()
    }
}

/** Lock when locked, else the caller's trailing content, else a length counter. */
@Composable
private fun FieldTrailing(locked: Boolean, trailing: (@Composable () -> Unit)?, counter: String?) {
    when {
        locked -> AtlasIcon(AtlasIcons.lock, tint = Atlas.colors.amber, size = 20.dp)
        trailing != null -> trailing()
        counter != null -> BasicText(counter, style = Atlas.type.src.copy(color = Atlas.colors.ink3))
    }
}

/** Amber "locked while moving" line with a lock (`.lockline`). */
@Composable
fun LockLine(reason: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AtlasIcon(AtlasIcons.lock, tint = Atlas.colors.amber, size = 16.dp)
        BasicText(reason, style = Atlas.type.note.copy(color = Atlas.colors.amber))
    }
}

private fun Modifier.baseline(color: Color, dashed: Boolean) = drawBehind {
    val w = 2.dp.toPx()
    val y = size.height - w / 2
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = w, pathEffect = effect)
}

@Preview
@Composable
private fun TextFieldPreview() = PreviewSurface {
    AtlasTextField("Alex", {}, label = "Your name · shown on your bike", maxLength = 19)
    AtlasTextField("Alex", {}, label = "Your name · shown on your bike", lockedReason = "Locked while moving (42 km/h). Stop to edit.")
}
