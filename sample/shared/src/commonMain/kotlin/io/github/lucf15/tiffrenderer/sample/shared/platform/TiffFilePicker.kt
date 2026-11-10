package io.github.lucf15.tiffrenderer.sample.shared.platform

import androidx.compose.runtime.Composable
import io.github.lucf15.tiffrenderer.TiffSource

class PickedTiff(val name: String, val source: TiffSource)

@Composable
expect fun rememberTiffPicker(onPicked: (PickedTiff) -> Unit): () -> Unit
