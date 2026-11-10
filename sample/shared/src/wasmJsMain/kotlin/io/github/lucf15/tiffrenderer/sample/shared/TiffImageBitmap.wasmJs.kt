package io.github.lucf15.tiffrenderer.sample.shared

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import io.github.lucf15.tiffrenderer.TiffBitmap
import io.github.lucf15.tiffrenderer.asArrayBuffer
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.webext.installPixelsFromArrayBuffer
import org.jetbrains.skiko.ExperimentalSkikoApi

@OptIn(ExperimentalSkikoApi::class)
actual suspend fun TiffBitmap.toImageBitmap(): ImageBitmap {
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val skiaBitmap = SkiaBitmap()
    try {
        check(skiaBitmap.installPixelsFromArrayBuffer(info, asArrayBuffer(), width * 4))
        skiaBitmap.setImmutable()
        return skiaBitmap.asComposeImageBitmap()
    } catch (t: Throwable) {
        skiaBitmap.close()
        throw t
    }
}
