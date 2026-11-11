package io.github.lucf15.tiffrenderer

import java.io.IOException

/** Routes native's plain `java.io.IOException` onto [TiffIOException]; `IllegalArgumentException`/
 * `IllegalStateException` already match the common API, so they propagate as-is. */
internal inline fun <T> rethrowingIOException(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        val message = e.message ?: "TIFF I/O error"
        throw TiffIOException(message, if (e is NativeTiffLimitException) TiffError.RESOURCE_LIMIT else TiffError.INVALID_DATA, e)
    }

internal class NativeTiffLimitException(message: String) : IOException(message)
