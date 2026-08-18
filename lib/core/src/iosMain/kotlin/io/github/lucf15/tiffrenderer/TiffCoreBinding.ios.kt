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
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSLock
import tiffcore.TIFFCORE_ERROR_ILLEGAL_STATE
import tiffcore.TIFFCORE_ERROR_INVALID_ARG
import tiffcore.TIFFCORE_OK
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

@OptIn(ExperimentalForeignApi::class)
actual class TiffCoreHandle internal constructor(internal val ptr: CPointer<TiffCoreDocument>)

/** Binds [TiffCoreBinding] straight to `tiff_core.h` via cinterop. */
@OptIn(ExperimentalForeignApi::class)
internal actual object TiffCoreBinding {
    actual fun open(source: TiffSource): TiffCoreHandle {
        globalInit
        return locked {
            memScoped {
                val errBuf = allocArray<ByteVar>(ERR_BUF_SIZE)
                val outDoc = alloc<CPointerVar<TiffCoreDocument>>()
                val status = tiffcore_open(source.fd, source.size, outDoc.ptr, errBuf, ERR_BUF_SIZE.toULong())
                if (status != TIFFCORE_OK) {
                    throwForStatus(status, errBuf, "cannot open TIFF")
                }
                TiffCoreHandle(checkNotNull(outDoc.value))
            }
        }
    }

    actual fun close(handle: TiffCoreHandle) {
        locked { tiffcore_close(handle.ptr) }
    }

    actual fun getPageCount(handle: TiffCoreHandle): Int =
        locked { tiffcore_get_page_count(handle.ptr) }

    actual fun openPage(handle: TiffCoreHandle, index: Int): TiffCorePageSize = locked {
        memScoped {
            val errBuf = allocArray<ByteVar>(ERR_BUF_SIZE)
            val outWidth = alloc<UIntVar>()
            val outHeight = alloc<UIntVar>()
            val status =
                tiffcore_open_page(handle.ptr, index, outWidth.ptr, outHeight.ptr, errBuf, ERR_BUF_SIZE.toULong())
            if (status != TIFFCORE_OK) {
                throwForStatus(status, errBuf, "cannot open TIFF page")
            }
            TiffCorePageSize(outWidth.value.toInt(), outHeight.value.toInt())
        }
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
            TiffRenderMode.FOR_DISPLAY -> TIFFCORE_RENDER_MODE_DISPLAY
            TiffRenderMode.FOR_PRINT -> TIFFCORE_RENDER_MODE_PRINT
        }
        locked {
            memScoped {
                val errBuf = allocArray<ByteVar>(ERR_BUF_SIZE)
                destination.pixels.usePinned { pinned ->
                    val status = tiffcore_render_page(
                        handle.ptr,
                        index,
                        pinned.addressOf(0).reinterpret<UIntVar>(),
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
                    if (status != TIFFCORE_OK) {
                        throwForStatus(status, errBuf, "failed to render TIFF page")
                    }
                }
            }
        }
    }

    actual fun retainRaster(handle: TiffCoreHandle, index: Int) {
        locked {
            memScoped {
                val errBuf = allocArray<ByteVar>(ERR_BUF_SIZE)
                val status = tiffcore_retain_raster(handle.ptr, index, errBuf, ERR_BUF_SIZE.toULong())
                if (status != TIFFCORE_OK) {
                    throwForStatus(status, errBuf, "failed to decode TIFF page")
                }
            }
        }
    }

    actual fun releaseRaster(handle: TiffCoreHandle) {
        locked { tiffcore_release_raster(handle.ptr) }
    }
}

private const val ERR_BUF_SIZE = 512L

// Must run exactly once before any other tiffcore_* call; `by lazy` gives that for free.
@OptIn(ExperimentalForeignApi::class)
private val globalInit: Unit by lazy { tiffcore_global_init() }

/** Serializes native calls (libtiff's error state is process-global); `NSLock` since Kotlin's
 * `synchronized()` is JVM-only. */
private val sTiffLock = NSLock()

private inline fun <T> locked(block: () -> T): T {
    sTiffLock.lock()
    try {
        return block()
    } finally {
        sTiffLock.unlock()
    }
}

// TiffCoreStatus is a UInt typealias, not a real Kotlin enum: cinterop maps plain C enums that way.
@OptIn(ExperimentalForeignApi::class)
private fun throwForStatus(status: TiffCoreStatus, errBuf: CPointer<ByteVar>, fallback: String): Nothing {
    val message = errBuf.toKString().ifEmpty { fallback }
    throw when (status) {
        TIFFCORE_ERROR_INVALID_ARG -> IllegalArgumentException(message)
        TIFFCORE_ERROR_ILLEGAL_STATE -> IllegalStateException(message)
        else -> TiffIOException(message)
    }
}
