package io.github.lucf15.tiffrenderer.sample.shared

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import io.github.lucf15.tiffrenderer.TiffBitmap
import io.github.lucf15.tiffrenderer.toIntArray
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

// tiff_core packs pixels as toByte(r) | (toByte(g) << 8) | (toByte(b) << 16) | (toByte(a) << 24).
// On every real (little-endian) iOS device/simulator that's R,G,B,A in ascending memory-address
// order, i.e. exactly RGBA_8888.
private fun IntArray.toRgba8888Bytes(): ByteArray {
    val bytes = ByteArray(size * 4)
    for (i in indices) {
        val p = this[i]
        bytes[i * 4] = (p and 0xFF).toByte()
        bytes[i * 4 + 1] = ((p shr 8) and 0xFF).toByte()
        bytes[i * 4 + 2] = ((p shr 16) and 0xFF).toByte()
        bytes[i * 4 + 3] = ((p shr 24) and 0xFF).toByte()
    }
    return bytes
}

actual fun TiffBitmap.toImageBitmap(): ImageBitmap {
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val skiaBitmap = SkiaBitmap()
    skiaBitmap.allocPixels(info)
    skiaBitmap.installPixels(toIntArray().toRgba8888Bytes())
    skiaBitmap.setImmutable()
    return skiaBitmap.asComposeImageBitmap()
}
