package io.github.lucf15.tiffrenderer.sample.shared

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import io.github.lucf15.tiffrenderer.TiffBitmap
import io.github.lucf15.tiffrenderer.toByteArray
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

actual fun TiffBitmap.toImageBitmap(): ImageBitmap {
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val skiaBitmap = SkiaBitmap()
    skiaBitmap.allocPixels(info)
    // toByteArray() already matches RGBA_8888's byte order, so no per-pixel repacking needed here
    // (unlike TiffImageBitmap.ios.kt, which has to unpack from TiffBitmap's IntArray storage).
    skiaBitmap.installPixels(toByteArray())
    skiaBitmap.setImmutable()
    return skiaBitmap.asComposeImageBitmap()
}
