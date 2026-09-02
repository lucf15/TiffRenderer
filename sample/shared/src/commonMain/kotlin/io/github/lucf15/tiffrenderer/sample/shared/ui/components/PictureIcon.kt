package io.github.lucf15.tiffrenderer.sample.shared.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.minDimension
import androidx.compose.ui.unit.dp
import io.github.lucf15.tiffrenderer.sample.shared.theme.AppTheme

/** A framed-photo glyph drawn as plain vectors, not a font glyph: emoji rendering needs a system
 * emoji font that wasmJs's Skia-in-browser has no access to (unlike Android/iOS/desktop, which
 * all have one), so this renders identically everywhere instead. */
@Composable
fun PictureIcon(modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary) {
    Canvas(modifier = modifier.size(48.dp)) {
        val strokeWidth = size.minDimension * 0.06f
        val inset = strokeWidth / 2
        val frameSize = Size(size.width - strokeWidth, size.height - strokeWidth)

        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = frameSize,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.12f),
            style = Stroke(width = strokeWidth),
        )

        val sunRadius = size.minDimension * 0.1f
        drawCircle(
            color = color,
            radius = sunRadius,
            center = Offset(size.width * 0.3f, size.height * 0.32f),
        )

        val baseline = size.height * 0.72f
        val peakHeight = size.height * 0.32f
        val mountainPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.18f, baseline)
            lineTo(size.width * 0.42f, baseline - peakHeight)
            lineTo(size.width * 0.6f, baseline - peakHeight * 0.5f)
            lineTo(size.width * 0.82f, baseline)
            close()
        }
        clipRect(
            left = inset,
            top = inset,
            right = size.width - inset,
            bottom = size.height - inset,
        ) {
            drawPath(path = mountainPath, color = color)
        }
    }
}
