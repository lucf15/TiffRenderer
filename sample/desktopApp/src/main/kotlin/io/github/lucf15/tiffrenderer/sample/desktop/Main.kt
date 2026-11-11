package io.github.lucf15.tiffrenderer.sample.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.lucf15.tiffrenderer.sample.shared.TiffRendererApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TiffRenderer",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        val titleBounds = remember(window) { DesktopTitleBounds() }
        LaunchedEffect(window) {
            if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
        }
        DisposableEffect(window) {
            val listener = object : java.awt.event.ComponentAdapter() {
                override fun componentShown(event: java.awt.event.ComponentEvent) {
                    if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
                        MacWindowControls.install(window.windowHandle)
                        titleBounds.apply(window.windowHandle)
                    }
                }
            }
            window.addComponentListener(listener)
            if (window.isShowing && System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
                MacWindowControls.install(window.windowHandle)
                titleBounds.apply(window.windowHandle)
            }
            onDispose { window.removeComponentListener(listener) }
        }
        TiffRendererApp(
            toolbarContainer = { content -> WindowDraggableArea { content() } },
            onTitleBoundsChanged = { bounds ->
                titleBounds.bounds = bounds
                if (window.isShowing && System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
                    titleBounds.apply(window.windowHandle)
                }
            },
            onBackgroundChanged = { color -> window.background = java.awt.Color(color.toArgb(), true) },
        )
    }
}

private class DesktopTitleBounds {
    var bounds = Rect.Zero

    fun apply(handle: Long) {
        MacWindowControls.titleRegion(handle, bounds.left.toDouble(), bounds.top.toDouble(),
            bounds.width.toDouble(), bounds.height.toDouble())
    }
}
