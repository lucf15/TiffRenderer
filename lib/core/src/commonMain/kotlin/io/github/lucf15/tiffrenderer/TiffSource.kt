package io.github.lucf15.tiffrenderer

/** A reusable input description; each renderer acquires and owns its own resources. */
public expect class TiffSource {
    internal fun acquire(): TiffOpenedSource

    public companion object {
        /** Takes an independent snapshot; prefer platform file inputs for large documents. */
        public fun fromByteArray(bytes: ByteArray): TiffSource
    }
}

internal expect class TiffOpenedSource {
    val size: Long
    val inMemory: Boolean
    fun close()
}

internal fun requireTiffInputSize(size: Long) {
    if (size < 0 || size > 256L * 1024 * 1024) throw TiffIOException("Input exceeds native limit", TiffError.RESOURCE_LIMIT)
}
