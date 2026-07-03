package io.github.linvva.mimottsengine.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF316B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EFE4),
    onPrimaryContainer = Color(0xFF123D32),
    secondary = Color(0xFF6B5E33),
    secondaryContainer = Color(0xFFF4E4B5),
    tertiary = Color(0xFF8A4B63),
    background = Color(0xFFFAFCF8),
    surface = Color(0xFFFAFCF8),
    surfaceVariant = Color(0xFFE1E8E1),
    surfaceContainer = Color(0xFFF0F5EF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FD7C3),
    onPrimary = Color(0xFF05372B),
    primaryContainer = Color(0xFF1C5144),
    onPrimaryContainer = Color(0xFFC0F0DF),
    secondary = Color(0xFFD9C88C),
    secondaryContainer = Color(0xFF4E4521),
    tertiary = Color(0xFFE7B5C7),
    background = Color(0xFF111412),
    surface = Color(0xFF111412),
    surfaceVariant = Color(0xFF3F4943),
    surfaceContainer = Color(0xFF181C1A),
    surfaceContainerHigh = Color(0xFF1D211F),
    surfaceContainerHighest = Color(0xFF252A27),
)

@Composable
fun MimoTtsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
