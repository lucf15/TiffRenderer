@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.lucf15.tiffrenderer

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Binds [TiffCoreBinding] to a dedicated Web Worker per document ([TiffCoreSession]) hosting the
 * Emscripten-compiled `tiffcore_module.mjs`: wasmJs has no real background-thread dispatcher, so a
 * direct main-thread call would freeze the browser for the decode's whole duration. One Worker per
 * document, not shared, so a crash or [close] can't leave a stale [TiffCoreHandle] aliasing a
 * different, still-live document. Bulk buffers cross as base64 (no fast ByteArray/IntArray bridge
 * on this platform); `dispatcher` is part of the shared [TiffCoreBinding] contract but unused here
 * since awaiting the Worker's response is already the suspension point. */
@OptIn(ExperimentalEncodingApi::class)
internal actual object TiffCoreBinding {
    actual suspend fun open(source: TiffSource, dispatcher: CoroutineDispatcher): TiffCoreHandle {
        val session = createTiffCoreSession()
        try {
            session.ensureLoaded().awaitTiffCore()
            val bytes = checkNotNull(source.bytes) { "TiffSource bytes already consumed" }
            val response = session.openMemory(Base64.encode(bytes)).awaitTiffCore()
            if (response.status != TIFFCORE_OK) {
                throwForStatus(response.status, response.message, "cannot open TIFF")
            }
            source.bytes = null
            return TiffCoreHandle(response.doc.toLong(), platformExtra = session)
        } catch (t: Throwable) {
            session.terminate()
            throw t
        }
    }

    // NonCancellable: this frees the Worker/session, so it must run even if the calling coroutine
    // is already cancelled, same reasoning as every other platform's close().
    actual suspend fun close(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher): Unit = withContext(NonCancellable) {
        try {
            handle.closeOnce { doc -> handle.session.close(doc.toInt()).awaitTiffCore() }
        } finally {
            handle.session.terminate()
        }
    }

    actual suspend fun getPageCount(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher): Int =
        handle.use { doc -> handle.session.getPageCount(doc.toInt()).awaitTiffCore().pageCount }

    actual suspend fun openPage(
        handle: TiffCoreHandle,
        index: Int,
        dispatcher: CoroutineDispatcher,
    ): TiffCorePageSize = handle.use { doc ->
        val response = handle.session.openPage(doc.toInt(), index).awaitTiffCore()
        if (response.status != TIFFCORE_OK) {
            throwForStatus(response.status, response.message, "cannot open TIFF page")
        }
        TiffCorePageSize(response.width, response.height)
    }

    actual suspend fun render(
        handle: TiffCoreHandle,
        index: Int,
        destination: TiffBitmap,
        clip: TiffRect,
        transform: TiffTransform,
        mode: TiffRenderMode,
        dispatcher: CoroutineDispatcher,
    ): Boolean {
        val nativeMode = when (mode) {
            TiffRenderMode.FOR_DISPLAY -> TIFFCORE_RENDER_MODE_DISPLAY
            TiffRenderMode.FOR_PRINT -> TIFFCORE_RENDER_MODE_PRINT
        }
        return handle.use { doc ->
            val v = transform.values
            val response = handle.session.renderPage(
                doc.toInt(), index, destination.width, destination.height,
                clip.left, clip.top, clip.right, clip.bottom,
                v[0], v[1], v[2], v[3], v[4], v[5],
                nativeMode,
            ).awaitTiffCore()
            if (response.status != TIFFCORE_OK && response.status != TIFFCORE_OK_PARTIAL) {
                throwForStatus(response.status, response.message, "failed to render TIFF page")
            }

            // Decodes straight into destination.bytes (no intermediate ByteArray, no per-pixel
            // pack/unpack loop): the Worker already hands back raw RGBA8888 bytes in the exact
            // layout TiffBitmap stores and Skia's installPixels wants.
            Base64.decodeIntoByteArray(checkNotNull(response.pixelsBase64), destination.bytes)

            response.status == TIFFCORE_OK_PARTIAL
        }
    }

    actual suspend fun retainRaster(handle: TiffCoreHandle, index: Int, dispatcher: CoroutineDispatcher): Boolean =
        handle.use { doc ->
            val response = handle.session.retainRaster(doc.toInt(), index).awaitTiffCore()
            if (response.status != TIFFCORE_OK && response.status != TIFFCORE_OK_PARTIAL) {
                throwForStatus(response.status, response.message, "failed to decode TIFF page")
            }
            response.status == TIFFCORE_OK_PARTIAL
        }

    actual suspend fun releaseRaster(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher): Unit = withContext(NonCancellable) {
        handle.use { doc -> handle.session.releaseRaster(doc.toInt()).awaitTiffCore() }
    }
}

// Mirrors tiff_core.h's TiffCoreStatus/TiffCoreRenderMode: no cinterop on this platform to bind
// them automatically, unlike iOS.
private const val TIFFCORE_OK = 0
private const val TIFFCORE_ERROR_INVALID_ARG = 2
private const val TIFFCORE_ERROR_ILLEGAL_STATE = 3
private const val TIFFCORE_OK_PARTIAL = 4
private const val TIFFCORE_RENDER_MODE_DISPLAY = 1
private const val TIFFCORE_RENDER_MODE_PRINT = 2

private val TiffCoreHandle.session: TiffCoreSession
    get() = platformExtra as TiffCoreSession

/** Like [Promise.await], but a rejection becomes [TiffIOException] instead of a bare
 * [RuntimeException], matching every other platform's contract; cancellation still propagates. */
private suspend fun <T : JsAny?> Promise<T>.awaitTiffCore(): T =
    try {
        await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        throw TiffIOException(e.message ?: "tiffcore worker call failed")
    }

private fun throwForStatus(status: Int, workerMessage: String?, fallback: String): Nothing {
    val message = workerMessage ?: fallback
    throw when (status) {
        TIFFCORE_ERROR_INVALID_ARG -> IllegalArgumentException(message)
        TIFFCORE_ERROR_ILLEGAL_STATE -> IllegalStateException(message)
        else -> TiffIOException(message)
    }
}
