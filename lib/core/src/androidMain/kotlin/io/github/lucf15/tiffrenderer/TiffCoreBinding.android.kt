package io.github.lucf15.tiffrenderer

import java.io.IOException

/** [lock] serializes native calls per document (a TIFF*'s directory cursor isn't thread-safe).
 * [ptr] zeroes under the same lock on close, so a blocked call sees a closed handle instead of a
 * freed pointer. */
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

/** Binds [TiffCoreBinding] to the JNI layer ([TiffRendererNative]). */
internal actual object TiffCoreBinding {
    actual fun open(source: TiffSource): TiffCoreHandle =
        TiffCoreHandle(rethrowingIOException { TiffRendererNative.nativeOpen(source.fd, source.size) })

    actual fun close(handle: TiffCoreHandle) {
        handle.closeOnce { ptr -> TiffRendererNative.nativeClose(ptr) }
    }

    actual fun getPageCount(handle: TiffCoreHandle): Int =
        handle.use { ptr -> TiffRendererNative.nativeGetPageCount(ptr) }

    actual fun openPage(handle: TiffCoreHandle, index: Int): TiffCorePageSize {
        val outSize = IntArray(2)
        handle.use { ptr ->
            rethrowingIOException { TiffRendererNative.nativeOpenPage(ptr, index, outSize) }
        }
        return TiffCorePageSize(outSize[0], outSize[1])
    }

    actual fun render(
        handle: TiffCoreHandle,
        index: Int,
        destination: TiffBitmap,
        clip: TiffRect,
        transform: TiffTransform,
        mode: TiffRenderMode,
    ): Boolean {
        require(!destination.bitmap.isRecycled) { "TiffBitmap wraps a recycled Bitmap" }
        // nativeRenderPage wants the full 3x3 (9 floats); TiffTransform only carries the 6
        // affine ones, so pad the always-identity perspective row back on.
        val matrixValues = floatArrayOf(
            transform.values[0], transform.values[1], transform.values[2],
            transform.values[3], transform.values[4], transform.values[5],
            0f, 0f, 1f,
        )
        val nativeMode = when (mode) {
            TiffRenderMode.FOR_DISPLAY -> 1
            TiffRenderMode.FOR_PRINT -> 2
        }
        return handle.use { ptr ->
            rethrowingIOException {
                TiffRendererNative.nativeRenderPage(
                    ptr, index, destination.bitmap,
                    clip.left, clip.top, clip.right, clip.bottom,
                    matrixValues, nativeMode,
                )
            }
        }
    }

    actual fun retainRaster(handle: TiffCoreHandle, index: Int): Boolean =
        handle.use { ptr ->
            rethrowingIOException { TiffRendererNative.nativeRetainRaster(ptr, index) }
        }

    actual fun releaseRaster(handle: TiffCoreHandle) {
        handle.use { ptr -> TiffRendererNative.nativeReleaseRaster(ptr) }
    }
}

/** Routes native's plain `java.io.IOException` onto [TiffIOException]; `IllegalArgumentException`/
 * `IllegalStateException` already match the common API, so they propagate as-is. */
private inline fun <T> rethrowingIOException(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw TiffIOException(e.message ?: "TIFF I/O error")
    }
