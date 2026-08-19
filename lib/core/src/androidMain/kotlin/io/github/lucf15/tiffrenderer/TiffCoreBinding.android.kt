package io.github.lucf15.tiffrenderer

import java.io.IOException

/** [lock] serializes native calls made through this handle: libtiff's own error/warning callback
 * writes to a thread_local buffer, so it doesn't need serializing, but a TIFF*'s directory cursor
 * (mutated by e.g. TIFFSetDirectory) isn't safe for concurrent use from two threads at once.
 * Scoped per document rather than process-wide so two unrelated [TiffRenderer]s don't block each
 * other's decodes. */
actual class TiffCoreHandle internal constructor(internal val ptr: Long) {
    internal val lock: Any = Any()
}

/** Binds [TiffCoreBinding] to the JNI layer ([TiffRendererNative]). */
internal actual object TiffCoreBinding {
    actual fun open(source: TiffSource): TiffCoreHandle =
        TiffCoreHandle(rethrowingIOException { TiffRendererNative.nativeOpen(source.fd, source.size) })

    actual fun close(handle: TiffCoreHandle) {
        synchronized(handle.lock) { TiffRendererNative.nativeClose(handle.ptr) }
    }

    actual fun getPageCount(handle: TiffCoreHandle): Int =
        synchronized(handle.lock) { TiffRendererNative.nativeGetPageCount(handle.ptr) }

    actual fun openPage(handle: TiffCoreHandle, index: Int): TiffCorePageSize {
        val outSize = IntArray(2)
        synchronized(handle.lock) {
            rethrowingIOException { TiffRendererNative.nativeOpenPage(handle.ptr, index, outSize) }
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
    ) {
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
        synchronized(handle.lock) {
            rethrowingIOException {
                TiffRendererNative.nativeRenderPage(
                    handle.ptr, index, destination.bitmap,
                    clip.left, clip.top, clip.right, clip.bottom,
                    matrixValues, nativeMode,
                )
            }
        }
    }

    actual fun retainRaster(handle: TiffCoreHandle, index: Int) {
        synchronized(handle.lock) {
            rethrowingIOException { TiffRendererNative.nativeRetainRaster(handle.ptr, index) }
        }
    }

    actual fun releaseRaster(handle: TiffCoreHandle) {
        synchronized(handle.lock) { TiffRendererNative.nativeReleaseRaster(handle.ptr) }
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
