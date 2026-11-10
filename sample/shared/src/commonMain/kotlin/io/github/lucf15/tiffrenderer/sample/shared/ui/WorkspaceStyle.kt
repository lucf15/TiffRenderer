package io.github.lucf15.tiffrenderer.sample.shared.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.AnchorSide
import com.composeunstyled.TooltipPanel
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledTooltip
import com.composeunstyled.theme.rememberColoredIndication

@Immutable
internal class WorkspaceColors(val dark: Boolean) {
    val ink = if (dark) Color(0xFFF1F3F8) else Color(0xFF202630)
    val secondary = if (dark) Color(0xFFA1A6B4) else Color(0xFF4F5968)
    val background = if (dark) Color(0xFF16191D) else Color(0xFFD4D7DC)
    val toolbar = if (dark) Color(0xFF292B30) else Color(0xFFFAFBFC)
    val sidebar = if (dark) Color(0xFF242529) else Color(0xFFF4F5F7)
    val surface = if (dark) Color(0xFF2A2F36) else Color(0xFFFFFFFF)
    val line = if (dark) Color(0x16FFFFFF) else Color(0x330F1C30)
    val hover = if (dark) Color(0x12FFFFFF) else Color(0x140F1C30)
    val accent = if (dark) Color(0xFF72ABFF) else Color(0xFF0066D6)
    val selected = if (dark) Color(0xFF007AFF) else Color(0xFF0066D6)
    val paper = if (dark) Color(0xFF414856) else Color.White
}

internal val LocalWorkspaceFontFamily =
    staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }
internal val LocalWorkspaceFocusVisible = staticCompositionLocalOf { false }
internal val LocalWorkspaceColors = staticCompositionLocalOf { WorkspaceColors(false) }

internal enum class WorkspaceIcon {
    Document,
    Folder,
    Sidebar,
    Info,
    Sun,
    Moon,
    ChevronLeft,
    ChevronRight,
    Plus,
    Minus,
    Fit,
    Close,
    Lock,
    Check,
    ZoomIn,
    ZoomOut,
    ZoomReset,
}

@Composable
internal fun WorkspaceText(
    text: String,
    size: Int = 13,
    color: Color = LocalWorkspaceColors.current.ink,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    BasicText(
        text,
        modifier,
        TextStyle(
            color = color,
            fontSize = size.sp,
            lineHeight = (size * 1.5f).sp,
            fontWeight = weight,
            fontFamily = LocalWorkspaceFontFamily.current,
            letterSpacing = if (size >= 20) (-0.5).sp else 0.sp,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun WorkspaceButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: WorkspaceIcon? = null,
    text: String? = null,
    primary: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = true,
) {
    val colors = LocalWorkspaceColors.current
    val interactions = remember { MutableInteractionSource() }
    val background =
        when {
            !enabled -> Color.Transparent
            primary || selected -> colors.selected
            else -> Color.Transparent
        }
    val indication =
        rememberColoredIndication(
            hoveredColor =
                if (primary || selected) Color.White.copy(alpha = 0.10f) else colors.hover,
            pressedColor =
                if (primary || selected) Color.Black.copy(alpha = 0.14f) else colors.line,
            focusedColor = Color.Transparent,
        )
    val foreground =
        when {
            !enabled -> colors.secondary.copy(alpha = 0.35f)
            primary -> Color.White
            selected -> Color.White
            else -> colors.ink
        }
    val shape = if (primary) RoundedCornerShape(12.dp) else CircleShape
    UnstyledTooltip(
        side = AnchorSide.Bottom,
        sideOffset = 7.dp,
        hoverDelayMillis = 550,
        panel = {
            TooltipPanel(enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.shadow(
                            12.dp,
                            RoundedCornerShape(8.dp),
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.12f),
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(0.5.dp, colors.line, RoundedCornerShape(8.dp))
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                ) {
                    WorkspaceText(label, 11)
                }
            }
        },
    ) {
        UnstyledButton(
            onClick,
            enabled = enabled,
            interactionSource = interactions,
            indication = indication,
            modifier =
                modifier
                    .heightIn(min = if (compact) 36.dp else 44.dp)
                    .widthIn(min = if (compact) 36.dp else 44.dp)
                    .clip(shape)
                    .background(background)
                    .workspaceFocusRing(interactions, shape)
                    .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                    .semantics { contentDescription = label },
            contentPadding =
                PaddingValues(
                    horizontal = if (text != null) 14.dp else 8.dp,
                    vertical = if (compact) 7.dp else 11.dp,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (icon != null) WorkspaceSymbol(icon, foreground, Modifier.size(18.dp))
                if (text != null) WorkspaceText(text, 13, foreground, FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun Modifier.workspaceFocusRing(source: MutableInteractionSource, shape: Shape): Modifier {
    val focused = source.collectIsFocusedAsState()
    val visible = LocalWorkspaceFocusVisible.current
    val color = LocalWorkspaceColors.current.accent
    return drawWithCache {
        val inset = 1.dp.toPx()
        val outline =
            shape.createOutline(
                Size(
                    (size.width - inset * 2).coerceAtLeast(0f),
                    (size.height - inset * 2).coerceAtLeast(0f),
                ),
                layoutDirection,
                this,
            )
        onDrawWithContent {
            drawContent()
            if (visible && focused.value)
                translate(inset, inset) { drawOutline(outline, color, style = Stroke(2.dp.toPx())) }
        }
    }
}

@Composable
internal fun WorkspaceSymbol(
    icon: WorkspaceIcon,
    color: Color = LocalWorkspaceColors.current.ink,
    modifier: Modifier = Modifier.size(20.dp),
) {
    Canvas(modifier) {
        withTransform({ scale(size.width / 24, size.height / 24, Offset.Zero) }) {
            val stroke = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x: Float, y: Float, xx: Float, yy: Float) =
                drawLine(color, Offset(x, y), Offset(xx, yy), 1.65f, StrokeCap.Round)
            fun path(vararg points: Float) {
                val p =
                    Path().apply {
                        moveTo(points[0], points[1])
                        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                    }
                drawPath(p, color, style = stroke)
            }
            when (icon) {
                WorkspaceIcon.Document -> {
                    path(14f, 3f, 6f, 3f, 6f, 21f, 19f, 21f, 19f, 8f, 14f, 3f, 14f, 8f, 19f, 8f)
                    line(9f, 12f, 15f, 12f)
                    line(9f, 16f, 15f, 16f)
                }
                WorkspaceIcon.Folder -> {
                    path(
                        3f,
                        8f,
                        3f,
                        5f,
                        10f,
                        5f,
                        12f,
                        8f,
                        21f,
                        8f,
                        21f,
                        19f,
                        3f,
                        19f,
                        3f,
                        8f,
                        21f,
                        8f,
                    )
                }
                WorkspaceIcon.Sidebar -> {
                    drawRoundRect(
                        color,
                        Offset(3f, 4f),
                        Size(18f, 16f),
                        CornerRadius(3f),
                        style = stroke,
                    )
                    line(9f, 4f, 9f, 20f)
                    line(5.5f, 8f, 6.5f, 8f)
                    line(5.5f, 11f, 6.5f, 11f)
                }
                WorkspaceIcon.Info -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    line(12f, 11f, 12f, 16f)
                    drawCircle(color, 1f, Offset(12f, 7.5f))
                }
                WorkspaceIcon.Sun -> {
                    drawCircle(color, 4f, Offset(12f, 12f), style = stroke)
                    line(12f, 2f, 12f, 4f)
                    line(12f, 20f, 12f, 22f)
                    line(2f, 12f, 4f, 12f)
                    line(20f, 12f, 22f, 12f)
                    line(5f, 5f, 6.5f, 6.5f)
                    line(17.5f, 17.5f, 19f, 19f)
                    line(5f, 19f, 6.5f, 17.5f)
                    line(17.5f, 6.5f, 19f, 5f)
                }
                WorkspaceIcon.Moon -> {
                    val p =
                        Path().apply {
                            moveTo(20f, 15f)
                            cubicTo(8f, 20f, 4f, 10f, 11f, 3f)
                            cubicTo(1f, 4f, 0f, 17f, 9f, 21f)
                            cubicTo(14f, 23f, 19f, 20f, 20f, 15f)
                        }
                    drawPath(p, color, style = stroke)
                }
                WorkspaceIcon.ChevronLeft -> path(14f, 6f, 8f, 12f, 14f, 18f)
                WorkspaceIcon.ChevronRight -> path(10f, 6f, 16f, 12f, 10f, 18f)
                WorkspaceIcon.Plus -> {
                    line(5f, 12f, 19f, 12f)
                    line(12f, 5f, 12f, 19f)
                }
                WorkspaceIcon.Minus -> line(5f, 12f, 19f, 12f)
                WorkspaceIcon.Fit -> {
                    path(3f, 9f, 3f, 4f, 8f, 4f)
                    path(16f, 4f, 21f, 4f, 21f, 9f)
                    path(3f, 15f, 3f, 20f, 8f, 20f)
                    path(16f, 20f, 21f, 20f, 21f, 15f)
                }
                WorkspaceIcon.Close -> {
                    line(6f, 6f, 18f, 18f)
                    line(18f, 6f, 6f, 18f)
                }
                WorkspaceIcon.Lock -> {
                    drawRoundRect(
                        color,
                        Offset(6f, 10f),
                        Size(12f, 10f),
                        CornerRadius(2f),
                        style = stroke,
                    )
                    drawArc(color, 180f, 180f, false, Offset(8f, 3f), Size(8f, 12f), style = stroke)
                    line(12f, 14f, 12f, 16f)
                }
                WorkspaceIcon.ZoomIn,
                WorkspaceIcon.ZoomReset,
                WorkspaceIcon.ZoomOut -> {
                    drawCircle(color, 6.5f, Offset(10.5f, 10.5f), style = stroke)
                    line(15f, 15f, 21f, 21f)
                    if (icon != WorkspaceIcon.ZoomReset) line(7.5f, 10.5f, 13.5f, 10.5f)
                    if (icon == WorkspaceIcon.ZoomReset) {
                        line(10.5f, 7.5f, 10.5f, 13.5f)
                        line(8.5f, 9f, 10.5f, 7.5f)
                        line(8.5f, 13.5f, 12.5f, 13.5f)
                    }
                    if (icon == WorkspaceIcon.ZoomIn) line(10.5f, 7.5f, 10.5f, 13.5f)
                }
                WorkspaceIcon.Check -> path(5f, 12f, 10f, 17f, 19f, 7f)
            }
        }
    }
}
