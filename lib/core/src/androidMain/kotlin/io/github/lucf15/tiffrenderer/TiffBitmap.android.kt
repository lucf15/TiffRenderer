package io.github.lucf15.tiffrenderer

import android.graphics.Bitmap

/** Wraps an existing [Bitmap] (must be [Bitmap.Config.ARGB_8888]), no copy: [TiffPage.render]
 * writes into it directly via the NDK's `AndroidBitmap` pixel-access path. */
actual class TiffBitmap(internal val bitmap: Bitmap) {
    init {
        require(!bitmap.isRecycled) { "TiffBitmap cannot wrap a recycled bitmap" }
        require(bitmap.config == Bitmap.Config.ARGB_8888) { "TiffBitmap requires an ARGB_8888 bitmap" }
        require(bitmap.isMutable) { "TiffBitmap requires a mutable bitmap" }
    }

    actual val width: Int get() = bitmap.width
    actual val height: Int get() = bitmap.height
}

/** Escape hatch for UI-layer code (e.g. `:sample:shared`) that needs the real `Bitmap` back to
 * display it; `commonMain` deliberately doesn't expose this since it's Android-only. */
fun TiffBitmap.asAndroidBitmap(): Bitmap = bitmap

actual fun createTiffBitmap(width: Int, height: Int): TiffBitmap =
    TiffBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))

internal actual fun TiffBitmap.pixelAt(x: Int, y: Int): Int = bitmap.getPixel(x, y)
