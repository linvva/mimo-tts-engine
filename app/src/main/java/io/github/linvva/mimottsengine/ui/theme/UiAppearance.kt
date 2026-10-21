package io.github.linvva.mimottsengine.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.linvva.mimottsengine.data.UiStyle

internal val LocalUiStyle = staticCompositionLocalOf { UiStyle.Classic }
// Keep form sampling separate from the bars' source, which also contains scrolling text.
internal val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

@Immutable
internal data class UiAppearance(
    val isGlass: Boolean,
    val cardShape: RoundedCornerShape,
    val fieldShape: RoundedCornerShape,
    val accentColor: Color,
    val borderColor: Color,
    val cardTint: Color,
    val fieldColor: Color,
    val focusedFieldColor: Color,
    val selectionColor: Color,
    val popupTint: Color,
)

@Composable
internal fun uiAppearance(): UiAppearance {
    val style = LocalUiStyle.current
    val colors = MaterialTheme.colorScheme
    return remember(style, colors) {
        val dark = colors.surface.luminance() < 0.5f
        val accent = lerp(colors.onSurface, colors.primary, 0.35f)
        UiAppearance(
            isGlass = style == UiStyle.LiquidGlass,
            cardShape = RoundedCornerShape(if (style == UiStyle.LiquidGlass) 28.dp else 8.dp),
            fieldShape = RoundedCornerShape(20.dp),
            accentColor = accent,
            borderColor = Color.White.copy(alpha = if (dark) 0.18f else 0.34f),
            cardTint = Color.White.copy(alpha = if (dark) 0.04f else 0.12f),
            fieldColor = Color.White.copy(alpha = if (dark) 0.06f else 0.22f),
            focusedFieldColor = Color.White.copy(alpha = if (dark) 0.12f else 0.38f),
            selectionColor = accent.copy(alpha = if (dark) 0.12f else 0.08f),
            popupTint = colors.surfaceContainerHigh.copy(alpha = if (dark) 0.55f else 0.40f),
        )
    }
}

@Composable
internal fun UiStyleProvider(style: UiStyle, content: @Composable () -> Unit) {
    val backdrop = rememberHazeState()
    CompositionLocalProvider(LocalUiStyle provides style, LocalGlassBackdrop provides backdrop, content = content)
}
