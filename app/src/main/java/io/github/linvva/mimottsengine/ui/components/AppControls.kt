package io.github.linvva.mimottsengine.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.linvva.mimottsengine.ui.theme.uiAppearance

private val LocalInsideGlassCard = staticCompositionLocalOf { false }

@Composable
internal fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val appearance = uiAppearance()
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (appearance.isGlass && pressed) 0.98f else 1f, label = "buttonPress")
    val glassBackground = if (appearance.isGlass) {
        Modifier.background(
            Brush.verticalGradient(listOf(
                appearance.borderColor.copy(alpha = if (enabled) appearance.borderColor.alpha else 0.04f),
                Color.White.copy(alpha = if (enabled) 0.04f else 0.02f),
            )),
            CircleShape,
        )
    } else Modifier
    Button(
        onClick = onClick,
        modifier = if (appearance.isGlass) {
            modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                .glassSurface(
                    shape = RoundedCornerShape(50),
                    tint = appearance.accentColor.copy(alpha = if (enabled) 0.045f else 0.02f),
                    interactionSource = interactions,
                )
                .then(glassBackground)
        } else modifier,
        enabled = enabled,
        interactionSource = interactions,
        shape = ButtonDefaults.shape,
        border = null,
        colors = if (appearance.isGlass) ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onSurface,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
        ) else ButtonDefaults.buttonColors(),
        content = content,
    )
}

@Composable
internal fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val appearance = uiAppearance()
    val interactions = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = if (appearance.isGlass) modifier.glassSurface(
            shape = RoundedCornerShape(50),
            tint = appearance.cardTint,
            interactionSource = interactions,
            soft = true,
        ) else modifier,
        enabled = enabled,
        interactionSource = interactions,
        colors = if (appearance.isGlass) ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) else ButtonDefaults.outlinedButtonColors(),
        border = if (appearance.isGlass) null else ButtonDefaults.outlinedButtonBorder(enabled),
        content = content,
    )
}

@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val appearance = uiAppearance()
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        supportingText = supportingText,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        readOnly = readOnly,
        visualTransformation = visualTransformation,
        shape = if (appearance.isGlass) appearance.fieldShape else OutlinedTextFieldDefaults.shape,
        colors = if (appearance.isGlass) OutlinedTextFieldDefaults.colors(
            focusedContainerColor = appearance.focusedFieldColor,
            unfocusedContainerColor = appearance.fieldColor,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            focusedLabelColor = appearance.accentColor,
            unfocusedLabelColor = colors.onSurfaceVariant,
            cursorColor = appearance.accentColor,
        ) else OutlinedTextFieldDefaults.colors(),
    )
}

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val appearance = uiAppearance()
    val colors = MaterialTheme.colorScheme
    val nested = LocalInsideGlassCard.current
    val tint = if (color == colors.errorContainer) color.copy(alpha = 0.65f) else appearance.cardTint
    Surface(
        modifier = if (appearance.isGlass && !nested) {
            modifier.glassSurface(appearance.cardShape, tint = tint, soft = true)
        } else modifier,
        shape = if (appearance.isGlass && nested) appearance.fieldShape else appearance.cardShape,
        color = when {
            !appearance.isGlass -> color
            nested -> appearance.fieldColor
            else -> Color.Transparent
        },
        tonalElevation = if (appearance.isGlass) 0.dp else tonalElevation,
    ) {
        CompositionLocalProvider(LocalInsideGlassCard provides appearance.isGlass, content = content)
    }
}

@Composable
internal fun AppSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, steps: Int) {
    val appearance = uiAppearance()
    val colors = MaterialTheme.colorScheme
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = if (appearance.isGlass) SliderDefaults.colors(
            thumbColor = appearance.accentColor,
            activeTrackColor = appearance.accentColor.copy(alpha = 0.70f),
            inactiveTrackColor = appearance.accentColor.copy(alpha = 0.10f),
            inactiveTickColor = appearance.accentColor.copy(alpha = 0.20f),
            activeTickColor = colors.surface.copy(alpha = 0.45f),
        ) else SliderDefaults.colors(),
    )
}

@Composable
internal fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val appearance = uiAppearance()
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        shape = if (appearance.isGlass) CircleShape else MaterialTheme.shapes.small,
        colors = if (appearance.isGlass) FilterChipDefaults.filterChipColors(
            containerColor = appearance.fieldColor,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = appearance.selectionColor,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ) else FilterChipDefaults.filterChipColors(),
        border = if (appearance.isGlass) BorderStroke(0.5.dp, appearance.borderColor)
            else FilterChipDefaults.filterChipBorder(enabled = true, selected = selected),
    )
}
