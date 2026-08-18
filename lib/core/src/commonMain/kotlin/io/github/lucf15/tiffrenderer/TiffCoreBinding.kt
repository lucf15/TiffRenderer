package io.github.lucf15.tiffrenderer

/** Opaque native document handle: never inspected in commonMain, just carried between calls. */
internal expect class TiffCoreHandle

internal class TiffCorePageSize(val width: Int, val height: Int)

/** The one platform seam for what can't be shared: native marshaling, serializing access to
 * libtiff's process-global error state, and translating native failures into [TiffIOException]. */
internal expect object TiffCoreBinding {
    fun open(source: TiffSource): TiffCoreHandle

    fun close(handle: TiffCoreHandle)

    fun getPageCount(handle: TiffCoreHandle): Int

    fun openPage(handle: TiffCoreHandle, index: Int): TiffCorePageSize

    fun render(
        handle: TiffCoreHandle,
        index: Int,
        destination: TiffBitmap,
        clip: TiffRect,
        transform: TiffTransform,
        mode: TiffRenderMode,
    )

    fun retainRaster(handle: TiffCoreHandle, index: Int)

    fun releaseRaster(handle: TiffCoreHandle)
}
