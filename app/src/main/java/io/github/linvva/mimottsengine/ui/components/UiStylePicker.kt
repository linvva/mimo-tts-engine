package io.github.linvva.mimottsengine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.linvva.mimottsengine.data.UiStyle
import io.github.linvva.mimottsengine.ui.theme.LocalUiStyle
import io.github.linvva.mimottsengine.ui.theme.uiAppearance

@Composable
internal fun UiStylePicker(onStyleChange: (UiStyle) -> Unit) {
    val selected = LocalUiStyle.current
    val appearance = uiAppearance()
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("界面风格", style = MaterialTheme.typography.titleSmall)
        UiStyle.entries.forEach { style ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (appearance.isGlass) Modifier.clip(appearance.fieldShape).background(
                        if (selected == style) appearance.selectionColor else Color.Transparent,
                    ) else Modifier)
                    .selectable(selected == style, role = Role.RadioButton, onClick = { onStyleChange(style) })
                    .testTag("style_${style.storageKey}")
                    .padding(horizontal = if (appearance.isGlass) 10.dp else 0.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RadioButton(
                    selected = selected == style,
                    onClick = null,
                    colors = if (appearance.isGlass) RadioButtonDefaults.colors(
                        selectedColor = appearance.accentColor,
                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    ) else RadioButtonDefaults.colors(),
                )
                Column {
                    Text(style.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (style == UiStyle.Classic) "标准导航与简洁表单" else "悬浮导航与柔和玻璃质感",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
