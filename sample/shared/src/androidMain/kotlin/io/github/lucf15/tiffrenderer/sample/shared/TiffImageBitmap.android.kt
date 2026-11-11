package io.github.lucf15.tiffrenderer.sample.shared

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.lucf15.tiffrenderer.TiffBitmap
import io.github.lucf15.tiffrenderer.asAndroidBitmap

actual suspend fun TiffBitmap.toImageBitmap(): ImageBitmap = asAndroidBitmap().asImageBitmap()
