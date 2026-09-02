package io.github.lucf15.tiffrenderer

import java.io.IOException

/** Routes native's plain `java.io.IOException` onto [TiffIOException]; `IllegalArgumentException`/
 * `IllegalStateException` already match the common API, so they propagate as-is. */
internal inline fun <T> rethrowingIOException(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw TiffIOException(e.message ?: "TIFF I/O error")
    }
