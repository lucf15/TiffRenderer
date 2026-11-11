package io.github.lucf15.tiffrenderer.sample.shared.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.Thumb
import com.composeunstyled.ThumbVisibility
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledVerticalScrollbar
import com.composeunstyled.rememberScrollbarState
import com.composeunstyled.theme.rememberColoredIndication
import io.github.lucf15.tiffrenderer.TiffSource
import io.github.lucf15.tiffrenderer.sample.resources.*
import io.github.lucf15.tiffrenderer.sample.shared.platform.primaryShortcutLabel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal enum class WorkspaceExample(
    val title: String,
    val file: String,
    val subtitle: String,
    val poster: DrawableResource,
    val tint: Color,
) {
    CATALOG(
        "Form & Field",
        "catalog",
        "4 pages · Editorial",
        Res.drawable.example_catalog,
        Color(0xFFD65E3E),
    ),
    TEXT("Typography", "text", "1 page · LZW", Res.drawable.example_text, Color(0xFF2481CC)),
    FAX("The archive", "fax", "12 pages · CCITT", Res.drawable.example_fax, Color(0xFF40A684)),
    MIXED(
        "Mixed formats",
        "mixed",
        "5 pages · Mixed sizes",
        Res.drawable.example_mixed,
        Color(0xFF9275CE),
    );

    suspend fun source(): TiffSource =
        TiffSource.fromByteArray(Res.readBytes("files/example_$file.tiff"))
}

@Composable
internal fun Sidebar(
    state: WorkspaceState,
    controller: WorkspaceController,
    choose: () -> Unit,
    modifier: Modifier,
    floating: Boolean = false,
    onSelected: () -> Unit = {},
) {
    val colors = LocalWorkspaceColors.current
    val density = LocalDensity.current
    Column(
        modifier.fillMaxHeight().pointerInput(floating) {
            if (floating) detectTapGestures {}
        },
    ) {
        if (state.name.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            LibraryAction(
                "Open document",
                "Choose a TIFF file",
                WorkspaceIcon.Folder,
                colors.accent,
                choose,
            )
            Spacer(Modifier.height(26.dp))
            WorkspaceText(
                "EXAMPLES",
                10,
                colors.secondary,
                FontWeight.SemiBold,
                Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(10.dp))
            WorkspaceExample.entries.forEach { example ->
                LibraryAction(
                    example.title,
                    example.subtitle,
                    WorkspaceIcon.Document,
                    example.tint,
                ) {
                    controller.openExample(example)
                    onSelected()
                }
            }
            Spacer(Modifier.weight(1f))
        } else if (state.pageCount > 0) {
            Row(
                Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                WorkspaceText("Pages", 11, weight = FontWeight.Medium)
                WorkspaceText(state.pageCount.toString(), 10, colors.secondary)
            }
            val grid = rememberLazyGridState()
            LaunchedEffect(grid, controller, state.pageCount) {
                snapshotFlow {
                    val width =
                        (grid.layoutInfo.viewportSize.width - with(density) { 10.dp.roundToPx() })
                            .coerceIn(1, 512)
                    Triple(
                        grid.layoutInfo.visibleItemsInfo.map { it.index },
                        width,
                        (width * 1.65f).toInt().coerceAtMost(896),
                    )
                }
                    .distinctUntilChanged()
                    .collect { (indices, width, height) ->
                        controller.visiblePages(indices, width, height)
                    }
            }
            DisposableEffect(controller) { onDispose { controller.visiblePages(emptyList()) } }
            FollowSelectedPage(grid, state)
            Box(Modifier.weight(1f)) {
                LazyVerticalGrid(
                    GridCells.Fixed(1),
                    Modifier.fillMaxSize()
                        .scrollEdgeFade(
                            colors.sidebar,
                            {
                                if (grid.firstVisibleItemIndex > 0) Float.MAX_VALUE
                                else grid.firstVisibleItemScrollOffset.toFloat()
                            },
                            {
                                val layout = grid.layoutInfo
                                val last = layout.visibleItemsInfo.lastOrNull()
                                when {
                                    last == null -> 0f
                                    last.index < state.pageCount - 1 -> Float.MAX_VALUE
                                    else ->
                                        (last.offset.y +
                                                last.size.height +
                                                layout.afterContentPadding -
                                                layout.viewportEndOffset)
                                            .coerceAtLeast(0)
                                            .toFloat()
                                }
                            },
                        )
                        .padding(horizontal = 16.dp),
                    state = grid,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
                ) {
                    items(state.pageCount, key = { it }) { index ->
                        PageThumbnail(index, state) {
                            controller.select(index)
                            onSelected()
                        }
                    }
                }
                UnstyledVerticalScrollbar(
                    rememberScrollbarState(grid),
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = 3.dp, top = 10.dp, bottom = 10.dp)
                        .width(5.dp)
                        .fillMaxHeight(),
                ) {
                    Thumb(
                        Modifier.fillMaxWidth()
                            .background(
                                colors.secondary.copy(alpha = 0.45f),
                                RoundedCornerShape(50),
                            ),
                        thumbVisibility =
                            ThumbVisibility.HideWhileIdle(
                                enter = fadeIn(),
                                exit = fadeOut(),
                                hideDelay = 600.milliseconds,
                            ),
                    )
                }
            }
        } else Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FollowSelectedPage(grid: LazyGridState, state: WorkspaceState) {
    val index = state.pageIndex
    val preview = state.previews[index]?.image
    val viewport = grid.layoutInfo.viewportSize
    val inset = with(LocalDensity.current) { 36.dp.roundToPx() }
    LaunchedEffect(grid, index, preview?.width, preview?.height, viewport) {
        if (viewport.height == 0) return@LaunchedEffect
        snapshotFlow { grid.layoutInfo.totalItemsCount }.first { it > index }
        if (grid.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            grid.animateScrollToItem(index)
        }
        val layout = grid.layoutInfo
        val item =
            layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return@LaunchedEffect
        val top =
            layout.viewportStartOffset + layout.beforeContentPadding + if (index > 0) inset else 0
        val bottom =
            layout.viewportEndOffset -
                layout.afterContentPadding -
                if (index < state.pageCount - 1) inset else 0
        val delta =
            when {
                item.size.height > bottom - top -> item.offset.y - top
                item.offset.y < top -> item.offset.y - top
                item.offset.y + item.size.height > bottom ->
                    item.offset.y + item.size.height - bottom
                else -> 0
            }
        if (delta != 0) grid.animateScrollBy(delta.toFloat())
    }
}

@Composable
private fun LibraryAction(
    title: String,
    subtitle: String,
    icon: WorkspaceIcon,
    tint: Color,
    onClick: () -> Unit,
) {
    val colors = LocalWorkspaceColors.current
    val source = remember { MutableInteractionSource() }
    val indication =
        rememberColoredIndication(
            hoveredColor = colors.hover,
            pressedColor = colors.line,
            focusedColor = Color.Transparent,
        )
    UnstyledButton(
        onClick,
        interactionSource = source,
        indication = indication,
        modifier =
            Modifier.padding(horizontal = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .workspaceFocusRing(source, RoundedCornerShape(10.dp))
                .pointerHoverIcon(PointerIcon.Hand),
        contentPadding = PaddingValues(11.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(36.dp)
                    .background(tint.copy(alpha = 0.11f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                WorkspaceSymbol(icon, tint, Modifier.size(21.dp))
            }
            Column {
                WorkspaceText(title, 12, weight = FontWeight.Medium)
                WorkspaceText(subtitle, 10, colors.secondary)
            }
        }
    }
}

@Composable
private fun PageThumbnail(index: Int, state: WorkspaceState, onClick: () -> Unit) {
    val colors = LocalWorkspaceColors.current
    val active = index == state.pageIndex
    val preview = state.previews[index]?.image
    val source = remember { MutableInteractionSource() }
    val background =
        if (active) colors.accent.copy(alpha = if (colors.dark) 0.16f else 0.08f)
        else Color.Transparent
    val indication =
        rememberColoredIndication(
            hoveredColor = colors.hover,
            pressedColor = colors.line,
            focusedColor = Color.Transparent,
        )
    val ratio = preview?.let { it.width.toFloat() / it.height } ?: 0.707f
    UnstyledButton(
        onClick,
        interactionSource = source,
        indication = indication,
        contentPadding = PaddingValues(0.dp),
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .workspaceFocusRing(source, RoundedCornerShape(8.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .semantics {
                    selected = active
                    contentDescription = "Page ${index+1}"
                },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(ratio)
                    .shadow(
                        2.dp,
                        RoundedCornerShape(3.dp),
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.08f),
                    )
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (preview != null) Color.White else colors.hover)
                    .border(
                        if (active) 1.5.dp else 0.5.dp,
                        if (active) colors.accent else colors.line,
                        RoundedCornerShape(3.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) Image(preview, null, Modifier.fillMaxSize())
                else
                    WorkspaceSymbol(
                        WorkspaceIcon.Document,
                        colors.secondary.copy(alpha = 0.45f),
                        Modifier.size(26.dp),
                    )
            }
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier.height(18.dp)
                    .widthIn(min = 26.dp)
                    .background(
                        if (active) colors.accent else Color.Transparent,
                        RoundedCornerShape(5.dp),
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                WorkspaceText(
                    (index + 1).toString(),
                    10,
                    if (active) Color.White else colors.secondary,
                    if (active) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
internal fun Welcome(choose: () -> Unit, wide: Boolean, onExample: (WorkspaceExample) -> Unit) {
    val colors = LocalWorkspaceColors.current
    if (wide) {
        Row(
            Modifier.widthIn(max = 880.dp).fillMaxWidth().padding(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            WelcomeIntroduction(choose, Modifier.weight(1f))
            Box(Modifier.width(0.5.dp).height(328.dp).background(colors.line))
            WelcomeExamples(onExample, Modifier.weight(1f))
        }
    } else {
        Column(
            Modifier.widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            WelcomeIntroduction(choose, Modifier.fillMaxWidth())
            WelcomeExamples(onExample, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WelcomeIntroduction(choose: () -> Unit, modifier: Modifier) {
    val colors = LocalWorkspaceColors.current
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Box(
            Modifier.size(64.dp)
                .background(colors.surface, RoundedCornerShape(16.dp))
                .border(0.5.dp, colors.line, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            WorkspaceSymbol(WorkspaceIcon.Document, colors.accent, Modifier.size(36.dp))
        }
        Spacer(Modifier.height(24.dp))
        WorkspaceText("Welcome to", 27, weight = FontWeight.Medium)
        WorkspaceText("TiffRenderer", 27, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        WorkspaceText("A closer look at every page.", 12, colors.secondary)
        Spacer(Modifier.height(30.dp))
        WorkspaceButton(
            "Open document",
            choose,
            icon = WorkspaceIcon.Folder,
            text = "Open document",
            primary = true,
            compact = false,
        )
        Spacer(Modifier.height(10.dp))
        WorkspaceText(
            "$primaryShortcutLabel O",
            10,
            colors.secondary,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun WelcomeExamples(onExample: (WorkspaceExample) -> Unit, modifier: Modifier) {
    val colors = LocalWorkspaceColors.current
    Column(modifier) {
        WorkspaceText(
            "Sample documents",
            13,
            weight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
        WorkspaceExample.entries.forEach { example -> ExampleRow(example) { onExample(example) } }
        Spacer(Modifier.height(14.dp))
        WorkspaceText(
            "Choose a sample to explore the viewer.",
            10,
            colors.secondary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ExampleRow(example: WorkspaceExample, onClick: () -> Unit) {
    val colors = LocalWorkspaceColors.current
    val source = remember { MutableInteractionSource() }
    UnstyledButton(
        onClick,
        interactionSource = source,
        indication =
            rememberColoredIndication(
                hoveredColor = colors.hover,
                pressedColor = colors.line,
                focusedColor = Color.Transparent,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .workspaceFocusRing(source, RoundedCornerShape(10.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .semantics { contentDescription = "Open example: ${example.title}" },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painterResource(example.poster),
                null,
                Modifier.size(width = 42.dp, height = 54.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
                    .border(0.5.dp, colors.line, RoundedCornerShape(3.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WorkspaceText(example.title, 12, weight = FontWeight.Medium)
                WorkspaceText(example.subtitle, 10, colors.secondary)
            }
            WorkspaceSymbol(
                WorkspaceIcon.ChevronRight,
                colors.secondary.copy(alpha = 0.55f),
                Modifier.size(15.dp),
            )
        }
    }
}
