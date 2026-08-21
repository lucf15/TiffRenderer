package io.github.lucf15.tiffrenderer

/** Binds [TiffCoreBinding] to the JNI layer ([TiffRendererNativeJvm]). */
internal actual object TiffCoreBinding {
    actual fun open(source: TiffSource): TiffCoreHandle =
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

    actual fun close(handle: TiffCoreHandle) {
        handle.closeOnce { ptr -> TiffRendererNativeJvm.nativeClose(ptr) }
    }

    actual fun getPageCount(handle: TiffCoreHandle): Int =
        handle.use { ptr -> TiffRendererNativeJvm.nativeGetPageCount(ptr) }

    actual fun openPage(handle: TiffCoreHandle, index: Int): TiffCorePageSize {
        val outSize = IntArray(2)
        handle.use { ptr ->
            rethrowingIOException { TiffRendererNativeJvm.nativeOpenPage(ptr, index, outSize) }
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
        val nativeMode = when (mode) {
            TiffRenderMode.FOR_DISPLAY -> 1
            TiffRenderMode.FOR_PRINT -> 2
        }
        return handle.use { ptr ->
            rethrowingIOException {
                TiffRendererNativeJvm.nativeRenderPage(
                    ptr, index, destination.buffer, destination.width, destination.height,
                    clip.left, clip.top, clip.right, clip.bottom, transform.values, nativeMode,
                )
            }
        }
    }

    actual fun retainRaster(handle: TiffCoreHandle, index: Int): Boolean =
        handle.use { ptr ->
            rethrowingIOException { TiffRendererNativeJvm.nativeRetainRaster(ptr, index) }
        }

    actual fun releaseRaster(handle: TiffCoreHandle) {
        handle.use { ptr -> TiffRendererNativeJvm.nativeReleaseRaster(ptr) }
    }
}
