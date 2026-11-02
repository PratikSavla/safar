package app.safar.bikesim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

private val Shape = RoundedCornerShape(8.dp)

@Composable
fun Txt(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color? = null) {
    BasicText(text, modifier, style.copy(color = color ?: LocalPalette.current.text))
}

/** A titled card that groups controls. */
@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(p.surface, Shape)
            .border(1.dp, p.line, Shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Txt(title.uppercase(), Type.section, color = p.muted)
        content()
    }
}

/** Outlined button; [primary] fills it with the accent colour. */
@Composable
fun SimButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val p = LocalPalette.current
    val fill = if (primary && enabled) p.accent else p.surface
    val text = when {
        !enabled -> p.muted
        primary -> p.onAccent
        else -> p.text
    }
    Box(
        modifier
            .background(fill, Shape)
            .border(1.dp, if (enabled) p.accent else p.line, Shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(label, Type.body, color = text) }
}

/** A label with an on/off switch drawn as two boxes. */
@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, Type.body, Modifier.weight(1f), color = if (enabled) p.text else p.muted)
        val track = if (checked) p.accent else p.line
        Box(Modifier.width(44.dp).background(track, RoundedCornerShape(12.dp)).padding(3.dp)) {
            Box(
                Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .size(18.dp)
                    .background(p.surface, RoundedCornerShape(9.dp)),
            )
        }
    }
}

/** One choice out of a few, as a scrollable row of chips. */
@Composable
fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, enabled: Boolean = true) {
    val p = LocalPalette.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (option in options) {
            val on = option == selected
            Box(
                Modifier
                    .background(if (on) p.accent else p.surface, Shape)
                    .border(1.dp, if (on) p.accent else p.line, Shape)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) { Txt(label(option), Type.body, color = if (on) p.onAccent else if (enabled) p.text else p.muted) }
        }
    }
}

/** "Label ........ value" on one line. */
@Composable
fun InfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Txt(label, Type.body, Modifier.weight(1f), color = LocalPalette.current.muted)
        Spacer(Modifier.width(8.dp))
        Txt(value, Type.body, color = valueColor)
    }
}
