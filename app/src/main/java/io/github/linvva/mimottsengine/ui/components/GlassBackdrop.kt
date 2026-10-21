package io.github.linvva.mimottsengine.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Composable
internal fun GlassBackdrop() {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val base = if (dark) Color(0xFF131925) else Color(0xFFEBEFF6)
    val blue = lerp(Color(0xFF719DE3), colors.primary, 0.20f)
    val violet = lerp(Color(0xFFB39CE0), colors.tertiary, 0.20f)
    val cyan = lerp(Color(0xFF67BECB), colors.secondary, 0.15f)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(base)
        drawRect(Brush.radialGradient(
            colors = listOf(blue.copy(alpha = if (dark) 0.42f else 0.55f), Color.Transparent),
            center = Offset(size.width * 0.05f, size.height * 0.15f),
            radius = size.width * 1.1f,
        ))
        drawRect(Brush.radialGradient(
            colors = listOf(violet.copy(alpha = if (dark) 0.35f else 0.48f), Color.Transparent),
            center = Offset(size.width * 1.05f, size.height * 0.55f),
            radius = size.width * 0.95f,
        ))
        drawRect(Brush.radialGradient(
            colors = listOf(cyan.copy(alpha = if (dark) 0.35f else 0.50f), Color.Transparent),
            center = Offset(size.width * 0.15f, size.height * 1.02f),
            radius = size.width * 1.2f,
        ))
        drawRect(Brush.linearGradient(
            0f to Color.Transparent,
            0.45f to Color.White.copy(alpha = if (dark) 0.04f else 0.32f),
            0.55f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(size.width, size.height * 0.65f),
        ))
        // Broad ribbons give the lens edges something to refract, including at the bottom bar.
        val ribbon = Path().apply {
            moveTo(size.width * 1.2f, size.height * 0.24f)
            cubicTo(-size.width * 0.35f, size.height * 0.58f,
                size.width * 1.25f, size.height * 0.73f,
                -size.width * 0.2f, size.height * 1.02f)
        }
        drawPath(
            ribbon,
            brush = Brush.linearGradient(listOf(
                Color.White.copy(alpha = if (dark) 0.06f else 0.26f),
                blue.copy(alpha = if (dark) 0.15f else 0.18f),
            )),
            style = Stroke(width = size.width * 0.16f),
        )
    }
}
