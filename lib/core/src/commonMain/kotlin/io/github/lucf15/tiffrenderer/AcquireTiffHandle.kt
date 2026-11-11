package io.github.lucf15.tiffrenderer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal suspend fun acquireTiffHandle(dispatcher: CoroutineDispatcher, open: () -> Long, free: (Long) -> Unit): TiffCoreHandle {
    var pointer = 0L
    try {
        return withContext(dispatcher) {
            pointer = open()
            check(pointer != 0L) { "Native open returned a null handle" }
            TiffCoreHandle(pointer)
        }
    } catch (t: Throwable) {
        if (pointer != 0L) {
            try { withContext(NonCancellable) { withContext(dispatcher) { free(pointer) } } }
            catch (cleanup: Throwable) { t.addSuppressed(cleanup) }
        }
        throw t
    }
}
