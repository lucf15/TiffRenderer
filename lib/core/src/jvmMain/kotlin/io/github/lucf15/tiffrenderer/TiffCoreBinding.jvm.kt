package io.github.lucf15.tiffrenderer

import java.io.IOException

actual class TiffCoreHandle internal constructor(internal val ptr: Long)

/** Binds [TiffCoreBinding] to the JNI layer ([TiffRendererNativeJvm]). */
internal actual object TiffCoreBinding {
    actual fun open(source: TiffSource): TiffCoreHandle =
        TiffCoreHandle(
            synchronized(sTiffLock) {
                rethrowingIOException {
                    val bytes = source.bytes
                    if (bytes != null) {
                        val handle = TiffRendererNativeJvm.nativeOpenBytes(bytes)
                        source.bytes = null
                        handle
                    } else {
                        TiffRendererNativeJvm.nativeOpen(checkNotNull(source.path))
                    }
                }
            },
        )

    actual fun close(handle: TiffCoreHandle) {
        synchronized(sTiffLock) { TiffRendererNativeJvm.nativeClose(handle.ptr) }
    }

    actual fun getPageCount(handle: TiffCoreHandle): Int =
        synchronized(sTiffLock) { TiffRendererNativeJvm.nativeGetPageCount(handle.ptr) }

    actual fun openPage(handle: TiffCoreHandle, index: Int): TiffCorePageSize {
        val outSize = IntArray(2)
        synchronized(sTiffLock) {
            rethrowingIOException { TiffRendererNativeJvm.nativeOpenPage(handle.ptr, index, outSize) }
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
        val nativeMode = when (mode) {
            TiffRenderMode.FOR_DISPLAY -> 1
            TiffRenderMode.FOR_PRINT -> 2
        }
        synchronized(sTiffLock) {
            rethrowingIOException {
                TiffRendererNativeJvm.nativeRenderPage(
                    handle.ptr, index, destination.pixels, destination.width, destination.height,
                    clip.left, clip.top, clip.right, clip.bottom, transform.values, nativeMode,
                )
            }
        }
    }

    actual fun retainRaster(handle: TiffCoreHandle, index: Int) {
        synchronized(sTiffLock) {
            rethrowingIOException { TiffRendererNativeJvm.nativeRetainRaster(handle.ptr, index) }
        }
    }

    actual fun releaseRaster(handle: TiffCoreHandle) {
        synchronized(sTiffLock) { TiffRendererNativeJvm.nativeReleaseRaster(handle.ptr) }
    }
}

/** Serializes all native calls: libtiff's error/warning handler state is process-global. */
private val sTiffLock = Any()

/** Routes native's plain `java.io.IOException` onto [TiffIOException]; `IllegalArgumentException`/
 * `IllegalStateException` already match the common API, so they propagate as-is. */
private inline fun <T> rethrowingIOException(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw TiffIOException(e.message ?: "TIFF I/O error")
    }
