package io.github.lucf15.tiffrenderer

public actual class TiffBitmap(public actual val width: Int, public actual val height: Int) {
    init {
        requirePositiveNonOverflowingBitmapDimensions(width, height)
    }

    // Packed RGBA8888, row-major, stride == width pixels: exactly what tiff_core's render_page
    // wants, so no copy/conversion happens at the render boundary.
    internal val pixels = IntArray(width * height)
}

/** Escape hatch for UI-layer code that wants the rendered pixels as packed ARGB ints (this
 * library's own [pixelAt] convention), as a copy so callers can't corrupt a page mid-render by
 * holding onto it. Prefer [toByteArray] when raw RGBA8888 bytes suffice: this repacks per pixel. */
public fun TiffBitmap.toIntArray(): IntArray = IntArray(pixels.size) { i -> packArgbFromRgbaPackedInt(pixels[i]) }

/** Escape hatch for UI-layer code that wants the raw RGBA8888 bytes: [pixels] is already stored
 * in that exact byte order (`r | g<<8 | b<<16 | a<<24` reads back as R,G,B,A on a little-endian
 * device), so this is a direct reinterpretation, not a repack. */
public fun TiffBitmap.toByteArray(): ByteArray {
    val bytes = ByteArray(pixels.size * 4)
    for (i in pixels.indices) {
        val p = pixels[i]
        bytes[i * 4] = (p and 0xFF).toByte()
        bytes[i * 4 + 1] = ((p ushr 8) and 0xFF).toByte()
        bytes[i * 4 + 2] = ((p ushr 16) and 0xFF).toByte()
        bytes[i * 4 + 3] = ((p ushr 24) and 0xFF).toByte()
    }
    return bytes
}

public actual fun createTiffBitmap(width: Int, height: Int): TiffBitmap = TiffBitmap(width, height)

internal actual fun TiffBitmap.pixelAt(x: Int, y: Int): Int = packArgbFromRgbaPackedInt(pixels[y * width + x])
