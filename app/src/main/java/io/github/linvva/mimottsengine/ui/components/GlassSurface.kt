@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package io.github.linvva.mimottsengine.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import io.github.linvva.mimottsengine.ui.theme.LocalGlassBackdrop

@Composable
internal fun Modifier.glassSurface(
    shape: RoundedCornerShape,
    source: HazeState? = LocalGlassBackdrop.current,
    tint: Color = Color.Transparent,
    interactionSource: InteractionSource? = null,
    soft: Boolean = false,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val style = remember(shape, colors, tint, soft) {
        GlassStyle.clear.then {
            shape(shape)
            backgroundColor(Color.Transparent)
            tint(tint)
            // Wallpaper is already diffuse. Avoid an extra blur pass for every card.
            optics(
                depth = 0f,
                blurRadius = 0.dp,
                refractionStrength = if (soft) 0.35f else 0.65f,
                refractionDisplacement = if (soft) 12.dp else 22.dp,
                refractionProfile = RefractionProfile.Edge(16.dp),
                refractionDetailIntensity = 0f,
            )
            specularIntensity(if (soft) 0.45f else 0.70f)
            ambientResponse(if (soft) 0.22f else 0.32f)
            lightPosition(Alignment.TopStart)
            edgeShadow(Color.Black.copy(alpha = if (soft) 0.035f else 0.07f))
            pressed { lightingIntensity(0.85f) }
        }
    }
    val material = if (source != null) {
        Modifier.hazeGlass(
            input = HazeInput.Sources(source),
            style = style,
            performanceMode = HazePerformanceMode.Default,
            interactionSource = interactionSource,
        )
    } else Modifier
    return shadow(if (soft) 3.dp else 6.dp, shape, clip = false)
        .then(material).glassRim(shape, intensity = if (soft) 0.50f else 0.80f)
}

internal fun Modifier.glassRim(shape: RoundedCornerShape, intensity: Float = 1f): Modifier = border(
    width = 1.dp,
    brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.85f * intensity),
        0.35f to Color.White.copy(alpha = 0.18f * intensity),
        0.65f to Color.White.copy(alpha = 0.04f * intensity),
        1f to Color.White.copy(alpha = 0.50f * intensity),
    ),
    shape = shape,
)
