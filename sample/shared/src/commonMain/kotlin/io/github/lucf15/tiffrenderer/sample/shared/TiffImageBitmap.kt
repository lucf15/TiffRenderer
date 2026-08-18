package io.github.lucf15.tiffrenderer.sample.shared

import androidx.compose.ui.graphics.ImageBitmap
import io.github.lucf15.tiffrenderer.TiffBitmap

/** Bridges `:lib:core`'s UI-framework-free [TiffBitmap] to Compose's [ImageBitmap] — this
 * module's whole reason to exist separately (mirrors how Coil splits `coil-compose` from `coil`). */
expect fun TiffBitmap.toImageBitmap(): ImageBitmap
