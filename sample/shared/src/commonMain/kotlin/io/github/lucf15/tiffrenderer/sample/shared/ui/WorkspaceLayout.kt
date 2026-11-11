package io.github.lucf15.tiffrenderer.sample.shared.ui

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.lucf15.tiffrenderer.TiffPageInfo
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun Modifier.liveWidth(width: () -> Dp): Modifier = layout { measurable, constraints ->
    val pixels = width().roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
    val child = measurable.measure(constraints.copy(minWidth = pixels, maxWidth = pixels))
    layout(child.width, child.height) { child.placeRelative(0, 0) }
}

internal fun Modifier.pageInsets(
    viewport: State<IntSize>, start: State<Dp>, end: State<Dp>, top: Dp, bottom: Dp,
): Modifier = layout { measurable, _ ->
    val left = start.value.roundToPx()
    val right = end.value.roundToPx()
    val child = measurable.measure(Constraints(minWidth = (viewport.value.width-left-right).coerceAtLeast(0)))
    layout(child.width+left+right, child.height+top.roundToPx()+bottom.roundToPx()) {
        child.placeRelative(left, top.roundToPx())
    }
}

internal fun Modifier.pageSize(
    viewport: State<IntSize>, start: State<Dp>, end: State<Dp>, top: Dp, bottom: Dp,
    info: TiffPageInfo, fitWidth: Boolean, zoom: () -> Float,
): Modifier = layout { measurable, _ ->
    val availableWidth = max(1, viewport.value.width-start.value.roundToPx()-end.value.roundToPx()-48.dp.roundToPx())
    val availableHeight = max(1, viewport.value.height-top.roundToPx()-bottom.roundToPx())
    val fit = if(fitWidth) availableWidth.toDouble()/info.width else min(availableWidth.toDouble()/info.width,availableHeight.toDouble()/info.height)
    val scale = min(fit*zoom(),30_000.0/max(info.width,info.height))
    val width = (info.width*scale).roundToInt().coerceAtLeast(1)
    val height = (info.height*scale).roundToInt().coerceAtLeast(1)
    val child = measurable.measure(Constraints.fixed(width,height))
    layout(width,height) { child.placeRelative(0,0) }
}

internal fun Modifier.viewportInsets(start: State<Dp>,end: State<Dp>,top: Dp,bottom: Dp): Modifier = layout { measurable, constraints ->
    val left=start.value.roundToPx()
    val right=end.value.roundToPx()
    val vertical=top.roundToPx()+bottom.roundToPx()
    val child=measurable.measure(constraints.offset(-left-right,-vertical))
    layout(child.width+left+right,child.height+vertical) {child.placeRelative(left,top.roundToPx())}
}
