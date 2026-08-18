package io.github.lucf15.tiffrenderer

actual class TiffBitmap(actual val width: Int, actual val height: Int) {
    init {
        require(width > 0 && height > 0) { "width/height must be positive, got ${width}x$height" }
    }

    internal val pixels = IntArray(width * height)
}

/** Escape hatch for UI-layer code that needs the rendered pixels. Returns a copy, not the live
 * backing array, so callers can't corrupt a page mid-render by holding onto it. */
fun TiffBitmap.toIntArray(): IntArray = pixels.copyOf()

actual fun createTiffBitmap(width: Int, height: Int): TiffBitmap = TiffBitmap(width, height)
