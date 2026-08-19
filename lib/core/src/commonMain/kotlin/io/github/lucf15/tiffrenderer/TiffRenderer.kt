package io.github.lucf15.tiffrenderer

/**
 * Decodes and rasterizes TIFF documents page by page. Its public shape mirrors Android's
 * `PdfRenderer`: same method names, same lifecycle, same page/render-mode pattern.
 *
 * A single concrete implementation shared by both platforms. Only the leaf native call inside
 * each operation is platform-specific, isolated behind [TiffCoreBinding]. Not thread safe; only
 * one [TiffPage] may be open at a time.
 */
class TiffRenderer(source: TiffSource) : AutoCloseable {
    private var handle: TiffCoreHandle? = null
    private var _pageCount: Int = 0
    private var openSource: TiffSource? = source
    private var currentPage: TiffPage? = null
    private var closed = false
    private var pageOpen = false

    val pageCount: Int
        get() {
            check(!closed) { "Already closed" }
            return _pageCount
        }

    init {
        check(!source.consumed) { "TiffSource has already been used to open a TiffRenderer" }
        source.consumed = true
        try {
            val h = TiffCoreBinding.open(source)
            handle = h
            _pageCount = TiffCoreBinding.getPageCount(h)
        } catch (t: Throwable) {
            doClose()
            throw t
        }
    }

    fun openPage(index: Int): TiffPage {
        check(!closed) { "Already closed" }
        check(!pageOpen) { "Current page not closed" }
        requirePageIndexInBounds(index, _pageCount)
        val h = checkNotNull(handle) { "TIFF document is not open" }

        val size = TiffCoreBinding.openPage(h, index)
        val page = TiffPage(this, h, index, size.width, size.height)
        currentPage = page
        pageOpen = true
        return page
    }

    override fun close() {
        check(!closed) { "Already closed" }
        check(!pageOpen) { "Current page not closed" }
        doClose()
    }

    internal fun onPageClosed() {
        currentPage = null
        pageOpen = false
    }

    private fun doClose() {
        currentPage?.let {
            it.closeInternal()
            currentPage = null
        }
        handle?.let { TiffCoreBinding.close(it) }
        handle = null
        openSource?.release()
        openSource = null
        closed = true
    }
}

/** A TIFF document page (directory) for rendering; see [TiffRenderer.openPage]. */
class TiffPage internal constructor(
    private val owner: TiffRenderer,
    private val handle: TiffCoreHandle,
    val index: Int,
    val width: Int,
    val height: Int,
) : AutoCloseable {
    private var rasterRetained = false
    private var closed = false

    /**
     * Opts this page into caching its decoded raster so repeated [render] calls reuse it instead
     * of redecoding; released automatically on [close].
     */
    fun retainRaster() {
        check(!closed) { "Already closed" }
        TiffCoreBinding.retainRaster(handle, index)
        rasterRetained = true
    }

    /**
     * Renders this page into [destination]. [destClip] restricts rendering to that rect within
     * [destination] (defaults to the full destination); [transform] maps page pixels to
     * destination pixels (defaults to fit-to-clip). Pixels outside the source page are left
     * untouched in [destination].
     */
    fun render(
        destination: TiffBitmap,
        destClip: TiffRect? = null,
        transform: TiffTransform? = null,
        renderMode: TiffRenderMode = TiffRenderMode.FOR_DISPLAY,
    ) {
        check(!closed) { "Already closed" }
        if (destClip != null) {
            requireClipInBounds(destClip, destination.width, destination.height)
        }

        val clip = destClip ?: TiffRect(0, 0, destination.width, destination.height)
        val effectiveTransform = transform ?: defaultFitToClipTransform(width, height, clip)
        TiffCoreBinding.render(handle, index, destination, clip, effectiveTransform, renderMode)
    }

    override fun close() {
        check(!closed) { "Already closed" }
        closeInternal()
    }

    internal fun closeInternal() {
        if (rasterRetained) {
            TiffCoreBinding.releaseRaster(handle)
            rasterRetained = false
        }
        closed = true
        owner.onPageClosed()
    }
}
