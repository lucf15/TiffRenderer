package io.github.lucf15.tiffrenderer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Binds [TiffCoreBinding] to the JNI layer ([TiffRendererNative]). Every call runs under the
 * injected [dispatcher] (see [TiffRenderer.open]): JNI calls block the calling thread for the
 * whole native decode, and `suspend` functions must be safe to call from the main thread without
 * the caller having to remember to dispatch themselves. */
internal actual object TiffCoreBinding {
    actual suspend fun open(source: TiffSource, dispatcher: CoroutineDispatcher): TiffCoreHandle =
        withContext(dispatcher) {
            TiffCoreHandle(rethrowingIOException { TiffRendererNative.nativeOpen(source.fd, source.size) })
        }

    // NonCancellable: this frees native state, so it must run even if the calling coroutine is
    // already cancelled. A plain withContext(dispatcher) throws immediately without entering the
    // block in that case, leaking the native handle.
    actual suspend fun close(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher) = withContext(dispatcher + NonCancellable) {
        handle.closeOnce { ptr -> TiffRendererNative.nativeClose(ptr) }
    }

    actual suspend fun getPageCount(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher): Int =
        withContext(dispatcher) {
            handle.use { ptr -> TiffRendererNative.nativeGetPageCount(ptr) }
        }

    actual suspend fun openPage(
        handle: TiffCoreHandle,
        index: Int,
        dispatcher: CoroutineDispatcher,
    ): TiffCorePageSize = withContext(dispatcher) {
        val outSize = IntArray(2)
        handle.use { ptr ->
            rethrowingIOException { TiffRendererNative.nativeOpenPage(ptr, index, outSize) }
        }
        TiffCorePageSize(outSize[0], outSize[1])
    }

    actual suspend fun render(
        handle: TiffCoreHandle,
        index: Int,
        destination: TiffBitmap,
        clip: TiffRect,
        transform: TiffTransform,
        mode: TiffRenderMode,
        dispatcher: CoroutineDispatcher,
    ): Boolean = withContext(dispatcher) {
        require(!destination.bitmap.isRecycled) { "TiffBitmap wraps a recycled Bitmap" }
        // nativeRenderPage wants the full 3x3 (9 floats); TiffTransform only carries the 6
        // affine ones, so pad the always-identity perspective row back on.
        val v = transform.values
        val matrixValues = floatArrayOf(v[0], v[1], v[2], v[3], v[4], v[5], 0f, 0f, 1f)
        val nativeMode = mode.toNativeMode()
        handle.use { ptr ->
            rethrowingIOException {
                TiffRendererNative.nativeRenderPage(
                    ptr, index, destination.bitmap,
                    clip.left, clip.top, clip.right, clip.bottom,
                    matrixValues, nativeMode,
                )
            }
        }
    }

    actual suspend fun retainRaster(handle: TiffCoreHandle, index: Int, dispatcher: CoroutineDispatcher): Boolean =
        withContext(dispatcher) {
            handle.use { ptr ->
                rethrowingIOException { TiffRendererNative.nativeRetainRaster(ptr, index) }
            }
        }

    actual suspend fun releaseRaster(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher) = withContext(dispatcher + NonCancellable) {
        handle.use { ptr -> TiffRendererNative.nativeReleaseRaster(ptr) }
    }
}
