package io.github.lucf15.tiffrenderer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Opaque native document handle: a pointer-sized [Long] behind one shared [Mutex] instead of a
 * per-platform lock. [ptr] zeroes under that lock on close, so a call already waiting sees
 * "closed" instead of a freed pointer. [platformExtra] carries whatever doesn't fit a [Long]
 * (wasmJs's per-document Worker session); `null` elsewhere. */
internal class TiffCoreHandle internal constructor(private var ptr: Long, internal val platformExtra: Any? = null) {
    private val mutex = Mutex()

    internal suspend fun <T> use(block: suspend (Long) -> T): T = mutex.withLock {
        check(ptr != 0L) { "TIFF document is not open" }
        block(ptr)
    }

    internal suspend fun closeOnce(free: suspend (Long) -> Unit) = mutex.withLock {
        val p = ptr
        if (p != 0L) {
            ptr = 0L
            free(p)
        }
    }
}
