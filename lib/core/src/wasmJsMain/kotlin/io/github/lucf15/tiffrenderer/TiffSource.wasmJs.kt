@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.lucf15.tiffrenderer

import org.w3c.files.Blob

public actual class TiffSource private constructor(private val snapshot: ByteArray?, private val file: Blob?) {
    internal actual fun acquire(): TiffOpenedSource = TiffOpenedSource(snapshot, file)

    public actual companion object {
        public actual fun fromByteArray(bytes: ByteArray): TiffSource {
            requireTiffInputSize(bytes.size.toLong())
            return TiffSource(bytes.copyOf(), null)
        }
        public fun fromBlob(blob: Blob): TiffSource = TiffSource(null, blob)
    }
}

internal actual class TiffOpenedSource(internal var bytes: ByteArray?, internal var blob: Blob?) {
    actual val size: Long = bytes?.size?.toLong() ?: blobByteCount(checkNotNull(blob)).toLong()
    actual val inMemory: Boolean = true
    actual fun close() { bytes = null; blob = null }
}

private fun blobByteCount(blob: Blob): Double = js("Number(blob.size)")
