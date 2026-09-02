package io.github.lucf15.tiffrenderer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Binds [TiffCoreBinding] to the JNI layer ([TiffRendererNativeJvm]). Every call runs under the
 * injected [dispatcher] (see [TiffRenderer.open]): JNI calls block the calling thread for the
 * whole native decode, and `suspend` functions must be safe to call from the main thread without
 * the caller having to remember to dispatch themselves. */
internal actual object TiffCoreBinding {
    actual suspend fun open(source: TiffSource, dispatcher: CoroutineDispatcher): TiffCoreHandle =
        withContext(dispatcher) {
            TiffCoreHandle(
                rethrowingIOException {
                    val bytes = source.bytes
                    if (bytes != null) {
                        val handle = TiffRendererNativeJvm.nativeOpenBytes(bytes)
                        source.bytes = null
                        handle
                    } else {
                        TiffRendererNativeJvm.nativeOpen(checkNotNull(source.path))
                    }
                },
            )
        }

    // NonCancellable: this frees native state, so it must run even if the calling coroutine is
    // already cancelled. A plain withContext(dispatcher) throws immediately without entering the
    // block in that case, leaking the native handle.
    actual suspend fun close(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher) = withContext(dispatcher + NonCancellable) {
        handle.closeOnce { ptr -> TiffRendererNativeJvm.nativeClose(ptr) }
    }

    actual suspend fun getPageCount(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher): Int =
        withContext(dispatcher) {
            handle.use { ptr -> TiffRendererNativeJvm.nativeGetPageCount(ptr) }
        }

    actual suspend fun openPage(
        handle: TiffCoreHandle,
        index: Int,
        dispatcher: CoroutineDispatcher,
    ): TiffCorePageSize = withContext(dispatcher) {
        val outSize = IntArray(2)
        handle.use { ptr ->
            rethrowingIOException { TiffRendererNativeJvm.nativeOpenPage(ptr, index, outSize) }
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
        val nativeMode = mode.toNativeMode()
        handle.use { ptr ->
            rethrowingIOException {
                TiffRendererNativeJvm.nativeRenderPage(
                    ptr, index, destination.buffer, destination.width, destination.height,
                    clip.left, clip.top, clip.right, clip.bottom, transform.values, nativeMode,
                )
            }
        }
    }

    actual suspend fun retainRaster(handle: TiffCoreHandle, index: Int, dispatcher: CoroutineDispatcher): Boolean =
        withContext(dispatcher) {
            handle.use { ptr ->
                rethrowingIOException { TiffRendererNativeJvm.nativeRetainRaster(ptr, index) }
            }
        }

    actual suspend fun releaseRaster(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher) = withContext(dispatcher + NonCancellable) {
        handle.use { ptr -> TiffRendererNativeJvm.nativeReleaseRaster(ptr) }
    }
}
