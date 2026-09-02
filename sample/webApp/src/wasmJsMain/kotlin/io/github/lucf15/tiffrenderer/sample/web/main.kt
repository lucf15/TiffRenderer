package io.github.lucf15.tiffrenderer.sample.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.lucf15.tiffrenderer.sample.shared.TiffRendererApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        TiffRendererApp()
    }
}
