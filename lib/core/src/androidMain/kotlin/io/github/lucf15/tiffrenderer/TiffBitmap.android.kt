package io.github.lucf15.tiffrenderer

import android.graphics.Bitmap

/** Wraps an existing [Bitmap] (must be [Bitmap.Config.ARGB_8888]), no copy: [TiffRenderer.renderInto]
 * writes into it directly via the NDK's `AndroidBitmap` pixel-access path. */
public actual class TiffBitmap(internal val bitmap: Bitmap) {
    public actual companion object {
        public actual fun create(width: Int, height: Int): TiffBitmap = createTiffBitmap(width, height)
    }

    internal actual val renderMutex = kotlinx.coroutines.sync.Mutex()

    init {
        require(!bitmap.isRecycled) { "TiffBitmap cannot wrap a recycled bitmap" }
        require(bitmap.config == Bitmap.Config.ARGB_8888) { "TiffBitmap requires an ARGB_8888 bitmap" }
        require(bitmap.isMutable) { "TiffBitmap requires a mutable bitmap" }
    }

    public actual val width: Int get() = bitmap.width
    public actual val height: Int get() = bitmap.height
}

/** Escape hatch for UI-layer code (e.g. `:sample:shared`) that needs the real `Bitmap` back to
 * display it; `commonMain` deliberately doesn't expose this since it's Android-only. */
public fun TiffBitmap.asAndroidBitmap(): Bitmap = bitmap

internal actual fun createTiffBitmap(width: Int, height: Int): TiffBitmap =
    TiffBitmap(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))

internal actual fun TiffBitmap.pixelAt(x: Int, y: Int): Int = bitmap.getPixel(x, y)

public actual fun TiffBitmap.copyPixels(): ByteArray {
    requirePositiveNonOverflowingBitmapDimensions(width, height)
    val colors = IntArray(width * height)
    bitmap.getPixels(colors, 0, width, 0, 0, width, height)
    return ByteArray(colors.size * 4).also { bytes ->
        for (index in colors.indices) {
            val color = colors[index]
            bytes[index * 4] = (color ushr 16).toByte()
            bytes[index * 4 + 1] = (color ushr 8).toByte()
            bytes[index * 4 + 2] = color.toByte()
            bytes[index * 4 + 3] = (color ushr 24).toByte()
        }
    }
}
