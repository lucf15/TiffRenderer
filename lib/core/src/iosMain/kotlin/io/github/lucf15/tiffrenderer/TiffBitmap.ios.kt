package io.github.lucf15.tiffrenderer

actual class TiffBitmap(actual val width: Int, actual val height: Int) {
    init {
        require(width > 0 && height > 0) { "width/height must be positive, got ${width}x$height" }
        require(width.toLong() * height.toLong() <= Int.MAX_VALUE) {
            "width * height overflows Int, got ${width}x$height"
        }
    }

    // Packed RGBA8888, row-major, stride == width pixels: exactly what tiff_core's render_page
    // wants, so no copy/conversion happens at the render boundary.
    internal val pixels = IntArray(width * height)
}

/** Escape hatch for UI-layer code that needs the rendered pixels. Returns a copy, not the live
 * backing array, so callers can't corrupt a page mid-render by holding onto it. */
fun TiffBitmap.toIntArray(): IntArray = pixels.copyOf()

actual fun createTiffBitmap(width: Int, height: Int): TiffBitmap = TiffBitmap(width, height)

internal actual fun TiffBitmap.pixelAt(x: Int, y: Int): Int {
    val p = pixels[y * width + x]
    val r = p and 0xFF
    val g = (p ushr 8) and 0xFF
    val b = (p ushr 16) and 0xFF
    val a = (p ushr 24) and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
