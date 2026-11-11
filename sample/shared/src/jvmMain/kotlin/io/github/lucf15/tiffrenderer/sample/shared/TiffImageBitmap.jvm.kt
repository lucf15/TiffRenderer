package io.github.lucf15.tiffrenderer.sample.shared

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import io.github.lucf15.tiffrenderer.TiffBitmap
import io.github.lucf15.tiffrenderer.copyPixels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap as SkiaBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

actual suspend fun TiffBitmap.toImageBitmap(): ImageBitmap {
    var owned: SkiaBitmap? = null
    try {
        return withContext(Dispatchers.Default) {
            val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
            val bitmap = SkiaBitmap().also { owned = it }
            check(bitmap.allocPixels(info))
            check(bitmap.installPixels(copyPixels()))
            bitmap.setImmutable()
            bitmap.asComposeImageBitmap()
        }
    } catch (error: Throwable) {
        owned?.close()
        throw error
    }
}
