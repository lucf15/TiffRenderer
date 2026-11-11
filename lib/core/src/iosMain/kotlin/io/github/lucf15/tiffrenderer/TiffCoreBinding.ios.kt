package io.github.lucf15.tiffrenderer

import cnames.structs.TiffCoreDocument
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toLong
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tiffcore.TIFFCORE_ERROR_ILLEGAL_STATE
import tiffcore.TIFFCORE_ERROR_INVALID_ARG
import tiffcore.TIFFCORE_OK
import tiffcore.TIFFCORE_OK_PARTIAL
import tiffcore.TIFFCORE_RENDER_MODE_DISPLAY
import tiffcore.TIFFCORE_RENDER_MODE_PRINT
import tiffcore.TiffCoreStatus
import tiffcore.tiffcore_close
import tiffcore.tiffcore_get_page_count
import tiffcore.tiffcore_global_init
import tiffcore.tiffcore_open
import tiffcore.tiffcore_open_page
import tiffcore.tiffcore_release_raster
import tiffcore.tiffcore_render_page
import tiffcore.tiffcore_retain_raster

/** Binds [TiffCoreBinding] straight to `tiff_core.h` via cinterop. [TiffCoreHandle] stores the
 * pointer as a platform-neutral [Long]; [asPointer] rehydrates it into a real [CPointer] at each
 * call site (`.rawValue`/`.toCPointer()` round-trip). Every call runs under the injected
 * [dispatcher] (see [TiffRenderer.open]): these are blocking native calls, and `suspend` functions
 * must be safe to call from the main thread without the caller having to remember to dispatch
 * themselves. */
@OptIn(ExperimentalForeignApi::class)
internal actual object TiffCoreBinding {
    actual suspend fun open(source: TiffOpenedSource, dispatcher: CoroutineDispatcher): TiffCoreHandle =
        acquireTiffHandle(dispatcher, {
            globalInit
            memScoped {
                val error = allocArray<ByteVar>(ERR_BUF_SIZE)
                val output = alloc<CPointerVar<TiffCoreDocument>>()
                val bytes = source.bytes
                val status = if (bytes != null) {
                    if (bytes.isEmpty()) throw TiffIOException("Empty TIFF input")
                    bytes.usePinned { tiffcore.tiffcore_open_memory(it.addressOf(0).reinterpret(), bytes.size.toLong(), output.ptr, error, ERR_BUF_SIZE.toULong()) }
                } else tiffcore_open(source.fd, source.size, output.ptr, error, ERR_BUF_SIZE.toULong())
                if (status != TIFFCORE_OK) throwForStatus(status, error, "cannot open TIFF")
                checkNotNull(output.value).rawValue.toLong()
            }
        }, { tiffcore_close(it.asPointer()) })

    // NonCancellable: this frees native state, so it must run even if the calling coroutine is
    // already cancelled. A plain withContext(dispatcher) throws immediately without entering the
    // block in that case, leaking the native handle.
    actual suspend fun close(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher) = withContext(dispatcher + NonCancellable) {
        handle.closeOnce { ptr -> tiffcore_close(ptr.asPointer()) }
    }

    actual suspend fun setLimits(handle: TiffCoreHandle, limits: TiffLimits, dispatcher: CoroutineDispatcher): Unit = withContext(dispatcher) {
        handle.use { ptr -> memScoped {
            val error = allocArray<ByteVar>(ERR_BUF_SIZE)
            val status = tiffcore.tiffcore_set_limits(ptr.asPointer(), limits.maxDecodedBytes.toULong(), limits.maxWorkingBytes.toULong(), error, ERR_BUF_SIZE.toULong())
            if (status != TIFFCORE_OK) throwForStatus(status, error, "cannot set limits")
        } }
    }

    actual suspend fun getPageCount(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher): Int =
        withContext(dispatcher) {
            handle.use { ptr -> tiffcore_get_page_count(ptr.asPointer()) }
        }

    actual suspend fun openPage(
        handle: TiffCoreHandle,
        index: Int,
        dispatcher: CoroutineDispatcher,
    ): TiffCorePageSize = withContext(dispatcher) {
        handle.use { ptr ->
            memScoped {
                val errBuf = allocArray<ByteVar>(ERR_BUF_SIZE)
                errBuf[0] = 0.toByte()
                val outWidth = alloc<UIntVar>()
                val outHeight = alloc<UIntVar>()
                val status = tiffcore_open_page(
                    ptr.asPointer(), index, outWidth.ptr, outHeight.ptr, errBuf, ERR_BUF_SIZE.toULong(),
                )
                if (status != TIFFCORE_OK) {
                    throwForStatus(status, errBuf, "cannot open TIFF page")
                }
                TiffCorePageSize(outWidth.value.toInt(), outHeight.value.toInt())
            }
        }
    }

    actual suspend fun render(
        handle: TiffCoreHandle,
        index: Int,
        destination: TiffBitmap,
        clip: TiffRect,
        transform: TiffTransform,
        mode: TiffSampling,
        dispatcher: CoroutineDispatcher,
    ): Boolean = withContext(dispatcher) {
        val nativeMode = when (mode) {
            TiffSampling.SMOOTH -> TIFFCORE_RENDER_MODE_DISPLAY
            TiffSampling.NEAREST -> TIFFCORE_RENDER_MODE_PRINT
        }
        handle.use { ptr ->
            memScoped {
                val errBuf = allocArray<ByteVar>(ERR_BUF_SIZE)
                errBuf[0] = 0.toByte()
                destination.pixels.usePinned { pinned ->
                    val status = tiffcore_render_page(
                        ptr.asPointer(),
                        index,
                        pinned.addressOf(0).reinterpret<UIntVar>(),
                        (destination.pixels.size.toLong() * 4).toULong(),
                        destination.width,
                        destination.width,
                        destination.height,
                        clip.left,
                        clip.top,
                        clip.right,
                        clip.bottom,
                        transform.values.refTo(0),
                        nativeMode,
                        errBuf,
                        ERR_BUF_SIZE.toULong(),
                    )
                    // TIFFCORE_OK_PARTIAL means libtiff tolerated a decode error in part of the
                    // page (e.g. one bad strip) and returned the rest of the raster anyway;
                    // treated as success, reported back via the return value.
                    if (status != TIFFCORE_OK && status != TIFFCORE_OK_PARTIAL) {
                        throwForStatus(status, errBuf, "failed to render TIFF page")
                    }
                    status == TIFFCORE_OK_PARTIAL
                }
            }
        }
    }

    actual suspend fun retainRaster(handle: TiffCoreHandle, index: Int, dispatcher: CoroutineDispatcher): Boolean =
        withContext(dispatcher) {
            handle.use { ptr ->
                memScoped {
                    val errBuf = allocArray<ByteVar>(ERR_BUF_SIZE)
                    errBuf[0] = 0.toByte()
                    val status = tiffcore_retain_raster(ptr.asPointer(), index, errBuf, ERR_BUF_SIZE.toULong())
                    if (status != TIFFCORE_OK && status != TIFFCORE_OK_PARTIAL) {
                        throwForStatus(status, errBuf, "failed to decode TIFF page")
                    }
                    status == TIFFCORE_OK_PARTIAL
                }
            }
        }

    actual suspend fun releaseRaster(handle: TiffCoreHandle, dispatcher: CoroutineDispatcher) = withContext(dispatcher + NonCancellable) {
        handle.use { ptr -> tiffcore_release_raster(ptr.asPointer()) }
    }
}

private const val ERR_BUF_SIZE = 512L

@OptIn(ExperimentalForeignApi::class)
private fun Long.asPointer(): CPointer<TiffCoreDocument> = checkNotNull(this.toCPointer())

// Must run exactly once before any other tiffcore_* call; `by lazy` gives that for free.
@OptIn(ExperimentalForeignApi::class)
private val globalInit: Unit by lazy { tiffcore_global_init() }

// TiffCoreStatus is a UInt typealias, not a real Kotlin enum: cinterop maps plain C enums that way.
@OptIn(ExperimentalForeignApi::class)
private fun throwForStatus(status: TiffCoreStatus, errBuf: CPointer<ByteVar>, fallback: String): Nothing {
    val message = errBuf.toKString().ifEmpty { fallback }
    throw when (status) {
        tiffcore.TIFFCORE_ERROR_LIMIT -> TiffIOException(message, TiffError.RESOURCE_LIMIT)
        TIFFCORE_ERROR_INVALID_ARG -> IllegalArgumentException(message)
        TIFFCORE_ERROR_ILLEGAL_STATE -> IllegalStateException(message)
        else -> TiffIOException(message)
    }
}
