@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.lucf15.tiffrenderer

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

/** RGBA8888 pixels stay in JavaScript memory until explicitly exported to Kotlin bytes. */
public actual class TiffBitmap(public actual val width: Int, public actual val height: Int) {
    public actual companion object {
        public actual fun create(width: Int, height: Int): TiffBitmap = createTiffBitmap(width, height)
    }

    internal actual val renderMutex = kotlinx.coroutines.sync.Mutex()

    init {
        requirePositiveNonOverflowingBitmapDimensions(width, height)
    }

    internal var buffer: ArrayBuffer? = null
}

/** Borrows the current RGBA8888 storage; do not detach it. A later render may replace it. */
public fun TiffBitmap.asArrayBuffer(): ArrayBuffer =
    buffer ?: ArrayBuffer(width * height * 4).also { buffer = it }

@OptIn(ExperimentalEncodingApi::class)
public actual fun TiffBitmap.copyPixels(): ByteArray = Base64.decode(bufferBase64(asArrayBuffer()))

private fun bufferBase64(buffer: ArrayBuffer): String = js("new Uint8Array(buffer).toBase64()")

internal actual fun createTiffBitmap(width: Int, height: Int): TiffBitmap = TiffBitmap(width, height)

internal actual fun TiffBitmap.pixelAt(x: Int, y: Int): Int {
    val bytes = Int8Array(asArrayBuffer())
    return packArgbFromRgbaBytes((y * width + x) * 4) { bytes[it] }
}
