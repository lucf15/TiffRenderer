package io.github.lucf15.tiffrenderer.sample.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.lucf15.tiffrenderer.sample.shared.TiffRendererApp

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "TiffRenderer") {
        TiffRendererApp()
    }
}
