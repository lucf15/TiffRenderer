package io.github.lucf15.tiffrenderer

import kotlinx.coroutines.CoroutineDispatcher

internal class TiffCorePageSize(val width: Int, val height: Int)

/** The one platform seam for what can't be shared: native marshaling, serializing access to
 * libtiff's process-global error state, and translating native failures into [TiffIOException].
 * `suspend` throughout so wasmJs can genuinely offload decoding to a Web Worker instead of
 * blocking the browser's single UI thread. JNI/cinterop calls aren't suspending points themselves,
 * so every other platform's `actual` runs its work inside `withContext(dispatcher)`: [dispatcher]
 * is threaded down from [TiffRenderer.open] rather than hardcoded, so it's a real injection seam
 * (a caller-supplied dispatcher, e.g. a test dispatcher) instead of a fixed choice baked into the
 * library that nothing can override. */
internal expect object TiffCoreBinding {
    suspend fun open(source: TiffSource, dispatcher: CoroutineDispatcher): TiffCoreHandle

    suspend fun close(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher)

    suspend fun getPageCount(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher): Int

    suspend fun openPage(handle: TiffCoreHandle, index: Int, dispatcher: CoroutineDispatcher): TiffCorePageSize

    /** Returns `true` if libtiff tolerated a partial decode error somewhere in the page. */
    suspend fun render(
        handle: TiffCoreHandle,
        index: Int,
        destination: TiffBitmap,
        clip: TiffRect,
        transform: TiffTransform,
        mode: TiffRenderMode,
        dispatcher: CoroutineDispatcher,
    ): Boolean

    /** Returns `true` if libtiff tolerated a partial decode error somewhere in the page. */
    suspend fun retainRaster(handle: TiffCoreHandle, index: Int, dispatcher: CoroutineDispatcher): Boolean

    suspend fun releaseRaster(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher)
}
