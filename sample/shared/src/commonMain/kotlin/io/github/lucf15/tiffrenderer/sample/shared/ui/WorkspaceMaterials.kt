package io.github.lucf15.tiffrenderer.sample.shared.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class WindowMaterial {
    TOOLBAR,
    PANEL,
    CONTROL,
}

@Composable
internal fun Modifier.windowMaterial(material: WindowMaterial): Modifier {
    val colors = LocalWorkspaceColors.current
    return background(
        when (material) {
            WindowMaterial.TOOLBAR -> colors.toolbar
            WindowMaterial.PANEL -> colors.sidebar
            WindowMaterial.CONTROL -> colors.surface
        },
    )
}

internal fun Modifier.scrollEdgeFade(
    color: Color,
    top: () -> Float,
    bottom: () -> Float,
    length: Dp = 36.dp,
): Modifier = drawWithCache {
    val pixels = length.toPx().coerceAtMost(size.height / 2)
    val stops =
        Array(17) { index ->
            val position = index / 16f
            val opacity = 1f - position * position * (3f - 2f * position)
            position to color.copy(alpha = opacity)
        }
    val topBrush = Brush.verticalGradient(*stops, startY = 0f, endY = pixels)
    val bottomBrush =
        Brush.verticalGradient(
            *stops
                .reversedArray()
                .map { (position, shade) -> 1f - position to shade }
                .toTypedArray(),
            startY = size.height - pixels,
            endY = size.height,
        )
    onDrawWithContent {
        drawContent()
        val topStrength = (top() / pixels).coerceIn(0f, 1f)
        val bottomStrength = (bottom() / pixels).coerceIn(0f, 1f)
        if (topStrength > 0f)
            drawRect(topBrush, size = Size(size.width, pixels), alpha = topStrength)
        if (bottomStrength > 0f)
            drawRect(
                bottomBrush,
                topLeft = Offset(0f, size.height - pixels),
                size = Size(size.width, pixels),
                alpha = bottomStrength,
            )
    }
}
