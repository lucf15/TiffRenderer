package io.github.lucf15.tiffrenderer

public actual class TiffBitmap(public actual val width: Int, public actual val height: Int) {
    init {
        requirePositiveNonOverflowingBitmapDimensions(width, height)
    }

    // Packed RGBA8888, row-major, stride == width pixels: exactly what tiff_core's render_page
    // wants, so no copy/conversion happens at the render boundary.
    internal val pixels = IntArray(width * height)
}

/** Escape hatch for UI-layer code that needs the rendered pixels. Returns a copy, not the live
 * backing array, so callers can't corrupt a page mid-render by holding onto it. */
public fun TiffBitmap.toIntArray(): IntArray = pixels.copyOf()

public actual fun createTiffBitmap(width: Int, height: Int): TiffBitmap = TiffBitmap(width, height)

internal actual fun TiffBitmap.pixelAt(x: Int, y: Int): Int {
    val p = pixels[y * width + x]
    val r = p and 0xFF
    val g = (p ushr 8) and 0xFF
    val b = (p ushr 16) and 0xFF
    val a = (p ushr 24) and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
