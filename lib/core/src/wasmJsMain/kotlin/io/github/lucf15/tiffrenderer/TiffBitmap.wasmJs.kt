package io.github.lucf15.tiffrenderer

/** Backed by a raw [ByteArray] (like JVM's `ByteBuffer`, unlike iOS's `IntArray`): the Worker's
 * response already arrives as decoded RGBA8888 bytes, so this avoids a needless pack-into-Int/
 * unpack-back-to-bytes round trip on the main thread between [TiffCoreBinding.render] and
 * `TiffImageBitmap.wasmJs.kt`'s Skia `installPixels` call — both can use these bytes directly. */
public actual class TiffBitmap(public actual val width: Int, public actual val height: Int) {
    init {
        requirePositiveNonOverflowingBitmapDimensions(width, height)
    }

    internal val bytes = ByteArray(width * height * 4)
}

public fun TiffBitmap.toByteArray(): ByteArray = bytes.copyOf()

public actual fun createTiffBitmap(width: Int, height: Int): TiffBitmap = TiffBitmap(width, height)

internal actual fun TiffBitmap.pixelAt(x: Int, y: Int): Int =
    packArgbFromRgbaBytes((y * width + x) * 4) { bytes[it] }
