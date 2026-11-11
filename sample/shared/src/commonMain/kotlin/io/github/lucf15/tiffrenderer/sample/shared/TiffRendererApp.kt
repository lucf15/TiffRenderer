package io.github.lucf15.tiffrenderer.sample.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.github.lucf15.tiffrenderer.sample.resources.Res
import io.github.lucf15.tiffrenderer.sample.resources.inter_medium
import io.github.lucf15.tiffrenderer.sample.resources.inter_regular
import io.github.lucf15.tiffrenderer.sample.resources.inter_semibold
import io.github.lucf15.tiffrenderer.sample.shared.platform.rememberTiffPicker
import io.github.lucf15.tiffrenderer.sample.shared.ui.LocalWorkspaceFontFamily
import io.github.lucf15.tiffrenderer.sample.shared.ui.WorkspaceController
import io.github.lucf15.tiffrenderer.sample.shared.ui.WorkspaceExample
import io.github.lucf15.tiffrenderer.sample.shared.ui.WorkspaceScreen
import org.jetbrains.compose.resources.Font

@Composable
fun TiffRendererApp(
    toolbarContainer: @Composable (@Composable () -> Unit) -> Unit = { content -> content() },
    onTitleBoundsChanged: (Rect) -> Unit = {},
    onBackgroundChanged: (Color) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { WorkspaceController(scope) }
    val choose = rememberTiffPicker(controller::open)
    LaunchedEffect(controller) { controller.ownLifetime() }
    LaunchedEffect(controller) { controller.openExample(WorkspaceExample.CATALOG) }
    val font =
        FontFamily(
            Font(Res.font.inter_regular, FontWeight.Normal),
            Font(Res.font.inter_medium, FontWeight.Medium),
            Font(Res.font.inter_semibold, FontWeight.SemiBold),
        )
    CompositionLocalProvider(LocalWorkspaceFontFamily provides font) {
        WorkspaceScreen(controller, choose, toolbarContainer, onTitleBoundsChanged, onBackgroundChanged)
    }
}
