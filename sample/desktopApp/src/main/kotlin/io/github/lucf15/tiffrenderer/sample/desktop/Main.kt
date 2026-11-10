package io.github.lucf15.tiffrenderer.sample.desktop

import androidx.compose.runtime.LaunchedEffect
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
        LaunchedEffect(window) {
            if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
        }
        TiffRendererApp(toolbarContainer = { content -> WindowDraggableArea { content() } })
    }
}
