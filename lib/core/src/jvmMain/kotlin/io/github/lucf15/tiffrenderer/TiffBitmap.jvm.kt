package io.github.lucf15.tiffrenderer

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Backed by a direct (off-heap) [ByteBuffer], not a JVM `IntArray`: [TiffCoreBinding]'s native
 * render call writes straight into it via `GetDirectBufferAddress`, so there's no JVM-heap array
 * for the JNI layer to pin or copy, and nothing for GC to have to work around during a render. */
actual class TiffBitmap(actual val width: Int, actual val height: Int) {
    init {
        require(width > 0 && height > 0) { "width/height must be positive, got ${width}x$height" }
        require(width.toLong() * height.toLong() * 4 <= Int.MAX_VALUE) {
            "width * height overflows Int, got ${width}x$height"
        }
    }

    internal val buffer: ByteBuffer =
        ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
}

/** Escape hatch for UI-layer code that needs the rendered pixels as packed ARGB ints (this
 * library's own [pixelAt] convention). Returns a copy, not the live backing buffer, so callers
 * can't corrupt a page mid-render by holding onto it. Prefer [toByteArray] when the consumer wants
 * raw RGBA8888 bytes anyway (e.g. handing them to a Skia bitmap) -- this repacks every pixel
 * through an Int, [toByteArray] doesn't. */
fun TiffBitmap.toIntArray(): IntArray {
    val bytes = toByteArray()
    val result = IntArray(width * height)
    for (i in result.indices) {
        val offset = i * 4
        val r = bytes[offset].toInt() and 0xFF
        val g = bytes[offset + 1].toInt() and 0xFF
        val b = bytes[offset + 2].toInt() and 0xFF
        val a = bytes[offset + 3].toInt() and 0xFF
        result[i] = r or (g shl 8) or (b shl 16) or (a shl 24)
    }
    return result
}

/** Escape hatch for UI-layer code (e.g. `:sample:shared`) that wants the raw RGBA8888 bytes
 * directly, matching Skia's `ColorType.RGBA_8888` byte order exactly -- no per-pixel repacking.
 * Returns a copy via an independent buffer view, so callers can't corrupt a page mid-render by
 * holding onto it, and this read never disturbs [buffer]'s own position/limit. */
fun TiffBitmap.toByteArray(): ByteArray {
    val bytes = ByteArray(buffer.capacity())
    buffer.duplicate().apply { rewind() }.get(bytes)
    return bytes
}

actual fun createTiffBitmap(width: Int, height: Int): TiffBitmap = TiffBitmap(width, height)

internal actual fun TiffBitmap.pixelAt(x: Int, y: Int): Int {
    val offset = (y * width + x) * 4
    val r = buffer.get(offset).toInt() and 0xFF
    val g = buffer.get(offset + 1).toInt() and 0xFF
    val b = buffer.get(offset + 2).toInt() and 0xFF
    val a = buffer.get(offset + 3).toInt() and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
