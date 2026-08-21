package io.github.lucf15.tiffrenderer

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
        val v = transform.values
        val matrixValues = floatArrayOf(v[0], v[1], v[2], v[3], v[4], v[5], 0f, 0f, 1f)
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
