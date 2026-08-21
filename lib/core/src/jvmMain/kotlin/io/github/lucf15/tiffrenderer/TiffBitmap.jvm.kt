package io.github.lucf15.tiffrenderer

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Backed by a direct (off-heap) [ByteBuffer], GC'd rather than deterministically freed; prefer
 * [wrapping] over repeated allocation for a page rendered many times (see the README's JVM section). */
public actual class TiffBitmap private constructor(
    public actual val width: Int,
    public actual val height: Int,
    internal val buffer: ByteBuffer,
) {
    public companion object {
        public operator fun invoke(width: Int, height: Int): TiffBitmap {
            requirePositiveNonOverflowingBitmapDimensions(width, height)
            return TiffBitmap(width, height, ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder()))
        }

        /** Wraps an existing direct [buffer] for reuse instead of allocating fresh; taken from its
         * current position, so a positioned/sliced view renders into that sub-region. */
        public fun wrapping(buffer: ByteBuffer, width: Int, height: Int): TiffBitmap {
            requirePositiveNonOverflowingBitmapDimensions(width, height)
            require(buffer.isDirect) { "TiffBitmap.wrapping requires a direct ByteBuffer" }
            require(!buffer.isReadOnly) { "TiffBitmap.wrapping requires a writable ByteBuffer" }
            require(buffer.remaining() >= width * height * 4) {
                "buffer has ${buffer.remaining()} bytes remaining, too small for ${width}x$height"
            }
            // slice(), not duplicate(): duplicate() ignores position, so an offset view of a
            // larger arena would silently render at the arena's start instead.
            return TiffBitmap(width, height, buffer.slice().order(ByteOrder.nativeOrder()))
        }
    }
}

/** Escape hatch for UI-layer code that wants the rendered pixels as packed ARGB ints (this
 * library's own [pixelAt] convention), as a copy so callers can't corrupt a page mid-render by
 * holding onto it. Prefer [toByteArray] when raw RGBA8888 bytes suffice; this repacks per pixel. */
public fun TiffBitmap.toIntArray(): IntArray {
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

/** Escape hatch for UI-layer code (e.g. `:sample:shared`) that wants the raw RGBA8888 bytes,
 * matching Skia's `ColorType.RGBA_8888` byte order with no per-pixel repacking. Returns a copy
 * via an independent buffer view, so it can't be used to corrupt a page mid-render. */
public fun TiffBitmap.toByteArray(): ByteArray {
    val byteCount = width * height * 4
    val bytes = ByteArray(byteCount)
    buffer.duplicate().apply { position(0); limit(byteCount) }.get(bytes)
    return bytes
}

public actual fun createTiffBitmap(width: Int, height: Int): TiffBitmap = TiffBitmap(width, height)

internal actual fun TiffBitmap.pixelAt(x: Int, y: Int): Int {
    val offset = (y * width + x) * 4
    val r = buffer.get(offset).toInt() and 0xFF
    val g = buffer.get(offset + 1).toInt() and 0xFF
    val b = buffer.get(offset + 2).toInt() and 0xFF
    val a = buffer.get(offset + 3).toInt() and 0xFF
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
