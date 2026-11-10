package io.github.lucf15.tiffrenderer.sample.shared.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composeunstyled.*
import io.github.lucf15.tiffrenderer.sample.shared.platform.nativeWindowControlsWidth
import io.github.lucf15.tiffrenderer.sample.shared.platform.primaryShortcutLabel
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@Composable
internal fun WorkspaceScreen(
    controller: WorkspaceController,
    chooseFile: () -> Unit,
    toolbarContainer: @Composable (@Composable () -> Unit) -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    var dark by remember { mutableStateOf(systemDark) }
    var sidebarVisible by remember { mutableStateOf(true) }
    var mobileSidebar by remember { mutableStateOf(false) }
    var inspector by remember { mutableStateOf(false) }
    var keyboardNavigation by remember { mutableStateOf(false) }
    var fitWidth by remember { mutableStateOf(true) }
    val sidebarWidth = remember { mutableFloatStateOf(184f) }
    val windowWidth = remember { mutableIntStateOf(1280) }
    val colors = remember(dark) { WorkspaceColors(dark) }
    val state = controller.state
    val focus = remember { FocusRequester() }
    val density = LocalDensity.current
    val wide by
        remember(density) { derivedStateOf { windowWidth.intValue / density.density >= 820f } }
    CompositionLocalProvider(
        LocalWorkspaceColors provides colors,
        LocalWorkspaceFocusVisible provides keyboardNavigation,
    ) {
        TooltipHost {
            Box(
                Modifier.fillMaxSize()
                    .onSizeChanged { windowWidth.intValue = it.width }
                    .background(colors.background)
                    .focusRequester(focus)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) if (
                                awaitPointerEvent(PointerEventPass.Initial).type ==
                                    PointerEventType.Press
                            ) {
                                keyboardNavigation = false
                                focus.requestFocus()
                            }
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) false
                        else
                            when {
                                event.key == Key.Tab -> {
                                    keyboardNavigation = true
                                    false
                                }
                                (event.isMetaPressed || event.isCtrlPressed) &&
                                    event.key == Key.O -> {
                                    chooseFile()
                                    true
                                }
                                (event.isMetaPressed || event.isCtrlPressed) &&
                                    event.key == Key.I -> {
                                    inspector = !inspector
                                    true
                                }
                                event.key == Key.Escape -> {
                                    inspector = false
                                    mobileSidebar = false
                                    if (state.stage == WorkspaceStage.OPENING) controller.cancel()
                                    true
                                }
                                state.stage != WorkspaceStage.READY -> false
                                event.key == Key.DirectionLeft || event.key == Key.DirectionUp -> {
                                    controller.select(state.pageIndex - 1)
                                    true
                                }
                                event.key == Key.DirectionRight ||
                                    event.key == Key.DirectionDown -> {
                                    controller.select(state.pageIndex + 1)
                                    true
                                }
                                event.key == Key.Equals || event.key == Key.Plus -> {
                                    controller.setZoom(state.zoom + 25)
                                    true
                                }
                                event.key == Key.Minus -> {
                                    controller.setZoom(state.zoom - 25)
                                    true
                                }
                                event.key == Key.Zero -> {
                                    controller.setZoom(100)
                                    true
                                }
                                else -> false
                            }
                    }
                    .focusable(),
            ) {
                val safe = WindowInsets.safeDrawing.asPaddingValues()
                val top = safe.calculateTopPadding()
                val bottom = safe.calculateBottomPadding()
                val start = safe.calculateStartPadding(LocalLayoutDirection.current)
                val end = safe.calculateEndPadding(LocalLayoutDirection.current)
                val toolbarHeight = if (wide) 58.dp else 60.dp
                val contentTop = top + toolbarHeight + 16.dp
                val empty = state.stage == WorkspaceStage.EMPTY
                val panelWidth =
                    remember(empty) {
                        derivedStateOf { if (empty) 264.dp else sidebarWidth.floatValue.dp }
                    }
                val sidebarProgress = remember {
                    Animatable(if (wide && sidebarVisible) 1f else 0f)
                }
                val inspectorProgress = remember { Animatable(if (wide && inspector) 1f else 0f) }
                LaunchedEffect(wide, sidebarVisible) {
                    sidebarProgress.animateTo(if (wide && sidebarVisible) 1f else 0f, tween(220))
                }
                LaunchedEffect(wide, inspector) {
                    inspectorProgress.animateTo(if (wide && inspector) 1f else 0f, tween(220))
                }
                val contentStart =
                    remember(start, panelWidth, wide, empty) {
                        derivedStateOf {
                            start +
                                if (wide && !empty)
                                    (panelWidth.value + 16.dp) * sidebarProgress.value
                                else 0.dp
                        }
                    }
                val contentEnd =
                    remember(end, wide) {
                        derivedStateOf {
                            end + if (wide) 276.dp * inspectorProgress.value else 0.dp
                        }
                    }
                val panelShape = RoundedCornerShape(14.dp)
                val panelEdge =
                    if (dark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.17f)
                LaunchedEffect(state.stage, mobileSidebar, inspector) { focus.requestFocus() }
                LaunchedEffect(wide) { if (wide) mobileSidebar = false }
                DocumentCanvas(
                    state,
                    controller,
                    chooseFile,
                    wide,
                    fitWidth,
                    contentStart,
                    contentEnd,
                    contentTop,
                    bottom,
                    Modifier.fillMaxSize(),
                )
                AnimatedVisibility(
                    wide && sidebarVisible && !empty,
                    modifier =
                        Modifier.align(Alignment.TopStart)
                            .padding(
                                start = start + 8.dp,
                                top = contentTop + 6.dp,
                                bottom = bottom + 8.dp,
                            ),
                    enter = slideInHorizontally(tween(220)) { -it } + fadeIn(),
                    exit = slideOutHorizontally(tween(200)) { -it } + fadeOut(),
                ) {
                    Box(
                        Modifier.liveWidth { panelWidth.value }
                            .fillMaxHeight()
                            .shadow(
                                12.dp,
                                panelShape,
                                ambientColor = Color.Black.copy(alpha = 0.06f),
                                spotColor = Color.Black.copy(alpha = 0.08f),
                            )
                            .clip(panelShape)
                            .windowMaterial(WindowMaterial.PANEL)
                            .border(0.5.dp, panelEdge, panelShape),
                    ) {
                        Sidebar(state, controller, chooseFile, Modifier.fillMaxSize())
                        Box(
                            Modifier.align(Alignment.CenterEnd)
                                .width(6.dp)
                                .fillMaxHeight()
                                .pointerHoverIcon(PointerIcon.Hand)
                                .draggable(
                                    rememberDraggableState { delta ->
                                        sidebarWidth.floatValue =
                                            (sidebarWidth.floatValue + delta / density.density)
                                                .coerceIn(144f, 260f)
                                    },
                                    Orientation.Horizontal,
                                ),
                        )
                    }
                }
                AnimatedVisibility(
                    inspector,
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .padding(
                                end = end + 8.dp,
                                top = contentTop + 6.dp,
                                bottom = bottom + 8.dp,
                            ),
                    enter = slideInHorizontally(tween(220)) { it } + fadeIn(),
                    exit = slideOutHorizontally(tween(200)) { it } + fadeOut(),
                ) {
                    Inspector(
                        state,
                        controller,
                        Modifier.width(260.dp)
                            .fillMaxHeight()
                            .shadow(
                                12.dp,
                                panelShape,
                                ambientColor = Color.Black.copy(alpha = 0.06f),
                                spotColor = Color.Black.copy(alpha = 0.08f),
                            )
                            .clip(panelShape)
                            .windowMaterial(WindowMaterial.PANEL)
                            .border(0.5.dp, panelEdge, panelShape),
                    ) {
                        inspector = false
                    }
                }
                Box(
                    Modifier.align(Alignment.TopCenter)
                        .padding(start = start + 8.dp, end = end + 8.dp, top = top + 8.dp)
                        .fillMaxWidth()
                        .shadow(
                            5.dp,
                            RoundedCornerShape(16.dp),
                            ambientColor = Color.Black.copy(alpha = 0.04f),
                            spotColor = Color.Black.copy(alpha = 0.06f),
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .windowMaterial(WindowMaterial.TOOLBAR)
                        .border(
                            0.5.dp,
                            if (dark) Color.White.copy(alpha = 0.15f)
                            else Color.Black.copy(alpha = 0.13f),
                            RoundedCornerShape(16.dp),
                        ),
                ) {
                    toolbarContainer {
                        PreviewToolbar(
                            state,
                            controller,
                            chooseFile,
                            wide,
                            if (wide) sidebarVisible else mobileSidebar,
                            inspector,
                            dark,
                            fitWidth,
                            contentStart,
                            {
                                if (wide) sidebarVisible = !sidebarVisible
                                else mobileSidebar = !mobileSidebar
                            },
                            { inspector = !inspector },
                            { dark = !dark },
                            {
                                fitWidth = !fitWidth
                                controller.setZoom(100)
                            },
                        )
                    }
                }
                if (!wide && !mobileSidebar && !inspector && state.stage == WorkspaceStage.READY) {
                    ViewControls(
                        state,
                        controller,
                        Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 12.dp),
                        false,
                    )
                }
                AnimatedVisibility(!wide && mobileSidebar, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)).clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            mobileSidebar = false
                        },
                    )
                }
                AnimatedVisibility(
                    !wide && mobileSidebar,
                    modifier =
                        Modifier.align(Alignment.TopStart)
                            .padding(
                                start = start + 8.dp,
                                top = contentTop + 6.dp,
                                bottom = bottom + 8.dp,
                            ),
                    enter = slideInHorizontally(tween(220)) { -it },
                    exit = slideOutHorizontally(tween(200)) { -it },
                ) {
                    Sidebar(
                        state,
                        controller,
                        {
                            mobileSidebar = false
                            chooseFile()
                        },
                        Modifier.width(200.dp)
                            .fillMaxHeight()
                            .shadow(18.dp, panelShape)
                            .clip(panelShape)
                            .windowMaterial(WindowMaterial.PANEL)
                            .border(0.5.dp, panelEdge, panelShape),
                        floating = true,
                        onSelected = { mobileSidebar = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewToolbar(
    state: WorkspaceState,
    controller: WorkspaceController,
    choose: () -> Unit,
    wide: Boolean,
    sidebar: Boolean,
    info: Boolean,
    dark: Boolean,
    fitWidth: Boolean,
    railWidth: State<Dp>,
    toggleSidebar: () -> Unit,
    toggleInfo: () -> Unit,
    toggleAppearance: () -> Unit,
    toggleFit: () -> Unit,
) {
    val colors = LocalWorkspaceColors.current
    Row(
        Modifier.fillMaxWidth()
            .height(if (wide) 58.dp else 60.dp)
            .padding(
                start = (if (wide) 14.dp else 8.dp) + nativeWindowControlsWidth.dp,
                end = if (wide) 14.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (wide) 14.dp else 4.dp),
    ) {
        Row(
            Modifier.liveWidth {
                if (wide)
                    (railWidth.value.value - 14 - nativeWindowControlsWidth).coerceAtLeast(36f).dp
                else 36.dp
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                WorkspaceButton(
                    if (state.stage == WorkspaceStage.EMPTY) "Open document"
                    else if (sidebar) "Hide thumbnails" else "Show thumbnails",
                    if (state.stage == WorkspaceStage.EMPTY) choose else toggleSidebar,
                    icon =
                        if (state.stage == WorkspaceStage.EMPTY) WorkspaceIcon.Folder
                        else WorkspaceIcon.Sidebar,
                )
            }
            if (wide && sidebar && state.stage != WorkspaceStage.EMPTY)
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    WorkspaceButton(
                        "Open TIFF · $primaryShortcutLabel O",
                        choose,
                        icon = WorkspaceIcon.Folder,
                    )
                }
        }
        DocumentCaption(state, Modifier.weight(1f).padding(start = if (wide) 8.dp else 2.dp))
        if (wide && state.stage == WorkspaceStage.READY) {
            ZoomTools(state, controller)
            PageTools(state, controller, fitWidth, toggleFit)
        }
        AppearanceTools(info, dark, wide, toggleInfo, toggleAppearance)
        if (state.stage != WorkspaceStage.EMPTY && (!wide || !sidebar))
            WorkspaceButton(
                "Open TIFF · $primaryShortcutLabel O",
                choose,
                icon = WorkspaceIcon.Folder,
            )
    }
}

@Composable
private fun DocumentCaption(state: WorkspaceState, modifier: Modifier) {
    val colors = LocalWorkspaceColors.current
    Column(modifier) {
        WorkspaceText(state.name.ifEmpty { "TiffRenderer" }, 13, weight = FontWeight.SemiBold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            WorkspaceText(
                when (state.stage) {
                    WorkspaceStage.READY -> "Page ${state.pageIndex+1} of ${state.pageCount}"
                    WorkspaceStage.OPENING -> "Opening document…"
                    WorkspaceStage.FAILED -> "Unable to open document"
                    WorkspaceStage.EMPTY -> "TIFF document viewer"
                },
                11,
                colors.secondary,
            )
        }
    }
}

@Composable
private fun ZoomTools(state: WorkspaceState, controller: WorkspaceController) {
    ToolbarGroup {
        WorkspaceButton(
            "Zoom out",
            { controller.setZoom(state.zoom - 25) },
            icon = WorkspaceIcon.ZoomOut,
            enabled = state.zoom > 50,
        )
        WorkspaceButton("Reset to fit", { controller.setZoom(100) }, icon = WorkspaceIcon.ZoomReset)
        WorkspaceButton(
            "Zoom in",
            { controller.setZoom(state.zoom + 25) },
            icon = WorkspaceIcon.ZoomIn,
            enabled = state.zoom < 200,
        )
    }
}

@Composable
private fun PageTools(
    state: WorkspaceState,
    controller: WorkspaceController,
    fitWidth: Boolean,
    toggleFit: () -> Unit,
) {
    ToolbarGroup {
        WorkspaceButton(
            if (fitWidth) "Fit whole page" else "Fit page width",
            toggleFit,
            icon = WorkspaceIcon.Fit,
        )
        WorkspaceButton(
            "Previous page",
            { controller.select(state.pageIndex - 1) },
            icon = WorkspaceIcon.ChevronLeft,
            enabled = state.pageIndex > 0,
        )
        WorkspaceButton(
            "Next page",
            { controller.select(state.pageIndex + 1) },
            icon = WorkspaceIcon.ChevronRight,
            enabled = state.pageIndex + 1 < state.pageCount,
        )
    }
}

@Composable
private fun AppearanceTools(
    info: Boolean,
    dark: Boolean,
    wide: Boolean,
    toggleInfo: () -> Unit,
    toggleAppearance: () -> Unit,
) {
    ToolbarGroup {
        WorkspaceButton(
            "Document information",
            toggleInfo,
            icon = WorkspaceIcon.Info,
            selected = info,
        )
        if (wide)
            WorkspaceButton(
                if (dark) "Light appearance" else "Dark appearance",
                toggleAppearance,
                icon = if (dark) WorkspaceIcon.Sun else WorkspaceIcon.Moon,
            )
    }
}

@Composable
private fun ToolbarGroup(content: @Composable RowScope.() -> Unit) {
    val colors = LocalWorkspaceColors.current
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .windowMaterial(WindowMaterial.CONTROL)
            .border(
                0.5.dp,
                if (colors.dark) Color.White.copy(alpha = 0.16f)
                else Color.Black.copy(alpha = 0.17f),
                RoundedCornerShape(50),
            )
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun DocumentCanvas(
    state: WorkspaceState,
    controller: WorkspaceController,
    choose: () -> Unit,
    wide: Boolean,
    fitWidth: Boolean,
    contentStart: State<Dp>,
    contentEnd: State<Dp>,
    contentTop: Dp,
    safeBottom: Dp,
    modifier: Modifier,
) {
    val colors = LocalWorkspaceColors.current
    val density = LocalDensity.current
    val viewport = remember { mutableStateOf(IntSize.Zero) }
    val zoom = remember { Animatable(1f) }
    val canPan by remember(state) { derivedStateOf { state.zoom > 100 } }
    LaunchedEffect(state) {
        snapshotFlow { state.zoom }
            .collectLatest {
                zoom.animateTo(it / 100f, spring(dampingRatio = 0.9f, stiffness = 480f))
            }
    }
    LaunchedEffect(
        controller,
        fitWidth,
        density,
        contentStart,
        contentEnd,
        contentTop,
        safeBottom,
        wide,
    ) {
        var first = true
        snapshotFlow {
            val size = viewport.value
            if (size.width == 0 || size.height == 0) null
            else {
                val width =
                    (size.width -
                            with(density) {
                                (contentStart.value + contentEnd.value + 48.dp).roundToPx()
                            })
                        .coerceAtLeast(1)
                val height =
                    if (fitWidth) Int.MAX_VALUE
                    else
                        (size.height -
                                with(density) {
                                    (contentTop + safeBottom + (if (wide) 36.dp else 96.dp))
                                        .roundToPx()
                                })
                            .coerceAtLeast(1)
                width to height
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { (width, height) ->
                if (!first) delay(120)
                first = false
                controller.viewport(width, height)
            }
    }
    Box(
        modifier
            .background(colors.background)
            .onSizeChanged { viewport.value = it },
        contentAlignment = Alignment.Center,
    ) {
        if (state.image != null && state.info != null && state.error == null) {
            val info = checkNotNull(state.info)
            val vertical = rememberScrollState()
            val horizontal = rememberScrollState()
            val scope = rememberCoroutineScope()
            LaunchedEffect(state.pageIndex) {
                vertical.scrollTo(0)
                horizontal.scrollTo(0)
            }
            Box(
                Modifier.fillMaxSize()
                    .pointerHoverIcon(if (canPan) PointerIcon.Hand else PointerIcon.Default)
                    .pointerInput(canPan) {
                        if (canPan)
                            detectDragGestures { change, drag ->
                                change.consume()
                                scope.launch {
                                    horizontal.scrollBy(-drag.x)
                                    vertical.scrollBy(-drag.y)
                                }
                            }
                    }
                    .verticalScroll(vertical)
                    .horizontalScroll(horizontal),
            ) {
                Box(
                    Modifier.pageInsets(
                        viewport,
                        contentStart,
                        contentEnd,
                        contentTop + 12.dp,
                        safeBottom + (if (wide) 24.dp else 88.dp),
                    ),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Image(
                        checkNotNull(state.image),
                        "Page ${state.pageIndex+1}",
                        Modifier.shadow(
                                5.dp,
                                RoundedCornerShape(1.dp),
                                ambientColor = Color.Black.copy(alpha = 0.09f),
                                spotColor = Color.Black.copy(alpha = 0.13f),
                            )
                            .pageSize(
                                viewport,
                                contentStart,
                                contentEnd,
                                contentTop,
                                safeBottom + (if (wide) 36.dp else 96.dp),
                                info,
                                fitWidth,
                            ) {
                                zoom.value
                            }
                            .background(Color.White)
                            .pointerInput(state) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        controller.setZoom(if (state.zoom == 100) 150 else 100)
                                    },
                                )
                            },
                    )
                }
            }
            UnstyledVerticalScrollbar(
                rememberScrollbarState(vertical),
                Modifier.align(Alignment.CenterEnd)
                    .padding(top = contentTop + 6.dp, bottom = safeBottom + 6.dp)
                    .width(6.dp)
                    .fillMaxHeight(),
            ) {
                Thumb(
                    Modifier.fillMaxWidth()
                        .background(colors.secondary.copy(alpha = 0.45f), RoundedCornerShape(50)),
                    thumbVisibility =
                        ThumbVisibility.HideWhileIdle(
                            enter = fadeIn(),
                            exit = fadeOut(),
                            hideDelay = 600.milliseconds,
                        ),
                )
            }
        } else {
            Box(
                Modifier.fillMaxSize()
                    .viewportInsets(contentStart, contentEnd, contentTop, safeBottom),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.stage == WorkspaceStage.EMPTY ->
                        Welcome(choose, wide, controller::openExample)
                    state.stage == WorkspaceStage.OPENING ->
                        Loading("Opening document", state.name, controller::cancel)
                    state.error != null ->
                        ErrorCard(checkNotNull(state.error)) {
                            if (state.stage == WorkspaceStage.READY)
                                controller.select(state.pageIndex)
                            else choose()
                        }
                    else ->
                        Loading("Preparing page", "Page ${state.pageIndex+1}", controller::cancel)
                }
            }
        }
    }
}

@Composable
private fun ViewControls(
    state: WorkspaceState,
    controller: WorkspaceController,
    modifier: Modifier,
    wide: Boolean,
) {
    val colors = LocalWorkspaceColors.current
    ControlDock(modifier) {
        Row(
            Modifier.padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            WorkspaceButton(
                "Previous page · ←",
                { controller.select(state.pageIndex - 1) },
                icon = WorkspaceIcon.ChevronLeft,
                enabled = state.pageIndex > 0,
            )
            Box(
                Modifier.widthIn(min = if (wide) 92.dp else 52.dp),
                contentAlignment = Alignment.Center,
            ) {
                WorkspaceText(
                    if (wide) "Page ${state.pageIndex+1} of ${state.pageCount}"
                    else "${state.pageIndex+1} / ${state.pageCount}",
                    12,
                    weight = FontWeight.Medium,
                )
            }
            WorkspaceButton(
                "Next page · →",
                { controller.select(state.pageIndex + 1) },
                icon = WorkspaceIcon.ChevronRight,
                enabled = state.pageIndex + 1 < state.pageCount,
            )
            Box(
                Modifier.padding(horizontal = 7.dp)
                    .width(1.dp)
                    .height(18.dp)
                    .background(colors.line),
            )
            WorkspaceButton(
                "Zoom out · −",
                { controller.setZoom(state.zoom - 25) },
                icon = WorkspaceIcon.Minus,
                enabled = state.zoom > 50,
            )
            WorkspaceButton("Reset zoom · 0", { controller.setZoom(100) }, text = "${state.zoom}%")
            WorkspaceButton(
                "Zoom in · +",
                { controller.setZoom(state.zoom + 25) },
                icon = WorkspaceIcon.Plus,
                enabled = state.zoom < 200,
            )
            if (wide)
                WorkspaceButton(
                    "Fit page · 0",
                    { controller.setZoom(100) },
                    icon = WorkspaceIcon.Fit,
                    selected = state.zoom == 100,
                )
        }
    }
}

@Composable
private fun Inspector(
    state: WorkspaceState,
    controller: WorkspaceController,
    modifier: Modifier = Modifier.width(264.dp),
    onClose: () -> Unit,
) {
    val colors = LocalWorkspaceColors.current
    Column(modifier.fillMaxHeight().pointerInput(Unit) { detectTapGestures {} }) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            WorkspaceText("Information", 14, weight = FontWeight.SemiBold)
            WorkspaceButton("Close information", onClose, icon = WorkspaceIcon.Close)
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.line))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val preview = state.previews[state.pageIndex]?.image ?: state.image
            if (preview != null) Image(preview, null, Modifier.size(110.dp, 146.dp))
            else WorkspaceSymbol(WorkspaceIcon.Document, colors.accent, Modifier.size(48.dp))
            Spacer(Modifier.height(20.dp))
            WorkspaceText(
                state.name.ifEmpty { "No document open" },
                14,
                weight = FontWeight.SemiBold,
                maxLines = 3,
            )
            Spacer(Modifier.height(6.dp))
            WorkspaceText("TIFF document", 11, colors.secondary)
            Spacer(Modifier.height(28.dp))
            InfoRow("Format", "TIFF")
            if (state.pageCount > 0) InfoRow("Pages", state.pageCount.toString())
            state.info?.let {
                InfoRow("Current page", (state.pageIndex + 1).toString())
                InfoRow("Dimensions", "${it.width} × ${it.height} px")
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.line))
            Spacer(Modifier.height(16.dp))
            WorkspaceText(
                "Rendered entirely on your device. No files are uploaded.",
                11,
                colors.secondary,
                maxLines = 3,
            )
        }
        if (state.name.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                WorkspaceButton(
                    "Close document",
                    {
                        controller.cancel()
                        onClose()
                    },
                    text = "Close document",
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        WorkspaceText(label, 11, LocalWorkspaceColors.current.secondary)
        WorkspaceText(value, 11, weight = FontWeight.Medium)
    }
}

@Composable
private fun ControlDock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalWorkspaceColors.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .shadow(
                5.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            )
            .windowMaterial(WindowMaterial.CONTROL)
            .border(
                0.5.dp,
                colors.line,
                shape,
            ),
    ) {
        content()
    }
}

@Composable
private fun Loading(title: String, subtitle: String, cancel: () -> Unit) {
    val colors = LocalWorkspaceColors.current
    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spinner(24)
        Spacer(Modifier.height(20.dp))
        WorkspaceText(title, 17, weight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        WorkspaceText(subtitle, 12, colors.secondary)
        Spacer(Modifier.height(20.dp))
        WorkspaceButton("Cancel loading", cancel, text = "Cancel")
    }
}

@Composable
private fun ErrorCard(message: String, retry: () -> Unit) {
    val colors = LocalWorkspaceColors.current
    Column(
        Modifier.widthIn(max = 360.dp).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WorkspaceSymbol(WorkspaceIcon.Document, colors.secondary, Modifier.size(40.dp))
        Spacer(Modifier.height(20.dp))
        WorkspaceText("Couldn't open this page", 19, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(9.dp))
        WorkspaceText(message, 13, colors.secondary, maxLines = 4)
        Spacer(Modifier.height(24.dp))
        WorkspaceButton("Try again", retry, text = "Try again", primary = true)
    }
}

@Composable
private fun Spinner(size: Int) {
    val colors = LocalWorkspaceColors.current
    val angle by
        rememberInfiniteTransition()
            .animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)))
    Canvas(Modifier.size(size.dp)) {
        drawArc(
            colors.accent,
            angle,
            250f,
            false,
            style =
                androidx.compose.ui.graphics.drawscope.Stroke(
                    1.8.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
        )
    }
}
