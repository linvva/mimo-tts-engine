@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package io.github.linvva.mimottsengine.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import io.github.linvva.mimottsengine.ui.MainTab
import io.github.linvva.mimottsengine.ui.theme.uiAppearance

@Composable
internal fun GlassBottomNavigation(pagerState: PagerState, hazeState: HazeState, onNavigate: (MainTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val appearance = uiAppearance()
    val shape = remember { RoundedCornerShape(50) }
    val style = remember(colors) {
        GlassStyle.clear.then {
            shape(shape)
            backgroundColor(Color.Transparent)
            tint(Color.White.copy(alpha = 0.08f))
            optics(
                blurRadius = 2.dp,
                refractionStrength = 0.90f,
                refractionDisplacement = 36.dp,
                refractionProfile = RefractionProfile.Edge(22.dp),
                refractionDetailIntensity = 0f,
            )
            specularIntensity(0.95f)
            ambientResponse(0.50f)
            lightPosition(Alignment.TopStart)
            chromaticAberrationStrength(0.04f)
            edgeSoftness(1.dp)
            pressed { lightingIntensity(0.90f) }
        }
    }
    Box(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, clip = false)
            .hazeGlass(HazeInput.Sources(hazeState), style, HazePerformanceMode.Default)
            .clip(shape)
            .glassRim(shape)
            .testTag("glass_navigation"),
    ) {
        Canvas(Modifier.matchParentSize().padding(6.dp)) {
            val width = size.width / MainTab.entries.size
            // Read the offset during drawing, so dragging does not recompose all three pages.
            val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, MainTab.entries.lastIndex.toFloat())
            val pillOffset = Offset(position * width, 0f)
            val pillSize = Size(width, size.height)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), appearance.selectionColor)),
                topLeft = pillOffset,
                size = pillSize,
                cornerRadius = CornerRadius(size.height / 2),
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.10f))),
                topLeft = pillOffset,
                size = pillSize,
                cornerRadius = CornerRadius(size.height / 2),
                style = Stroke(1.dp.toPx()),
            )
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).selectableGroup()) {
            MainTab.entries.forEach { tab ->
                val selected = pagerState.currentPage == tab.ordinal
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .selectable(selected, role = Role.Tab, onClick = { onNavigate(tab) })
                        .testTag("nav_${tab.storageKey}")
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    val color = if (selected) appearance.accentColor else colors.onSurfaceVariant
                    Icon(tab.icon, contentDescription = null, tint = color)
                    Text(tab.title, color = color, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
