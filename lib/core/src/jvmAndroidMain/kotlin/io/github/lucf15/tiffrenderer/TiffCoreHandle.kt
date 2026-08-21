package io.github.lucf15.tiffrenderer

import java.io.IOException

/** [lock] serializes native calls per document (a TIFF*'s directory cursor isn't thread-safe).
 * [ptr] zeroes under the same lock on close, so a blocked call sees a closed handle instead of a
 * freed pointer. Shared between Android and JVM desktop: both are real JVMs, so `synchronized()`
 * is available to both (iOS's own actual uses `NSLock` instead). */
internal actual class TiffCoreHandle internal constructor(private var ptr: Long) {
    private val lock: Any = Any()

    internal fun <T> use(block: (Long) -> T): T = synchronized(lock) {
        check(ptr != 0L) { "TIFF document is not open" }
        block(ptr)
    }

    internal fun closeOnce(free: (Long) -> Unit) = synchronized(lock) {
        val p = ptr
        if (p != 0L) {
            ptr = 0L
            free(p)
        }
    }
}

/** Routes native's plain `java.io.IOException` onto [TiffIOException]; `IllegalArgumentException`/
 * `IllegalStateException` already match the common API, so they propagate as-is. */
internal inline fun <T> rethrowingIOException(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw TiffIOException(e.message ?: "TIFF I/O error")
    }
