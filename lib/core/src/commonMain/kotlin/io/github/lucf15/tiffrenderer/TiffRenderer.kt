package io.github.lucf15.tiffrenderer

/** Decodes and rasterizes TIFF documents page by page; mirrors Android's `PdfRenderer` (same
 * method names, lifecycle, and page/render-mode pattern). Concurrent [TiffPage.render] calls
 * through the same page are memory-safe; the lifecycle (`openPage`/`close`) is not. */
public class TiffRenderer(source: TiffSource) : AutoCloseable {
    private var handle: TiffCoreHandle? = null

    // -1 means "not yet resolved"; see the pageCount KDoc below for why.
    private var _pageCount: Int = -1
    private var openSource: TiffSource? = source
    private var currentPage: TiffPage? = null
    private var closed = false
    private var pageOpen = false

    /** Number of pages in this document. Unlike PDF's `/Count`, TIFF has no page-count field: this
     * walks the whole IFD chain, so it's resolved lazily on first access and cached. */
    public val pageCount: Int
        get() {
            check(!closed) { "Already closed" }
            if (_pageCount < 0) {
                _pageCount = TiffCoreBinding.getPageCount(checkNotNull(handle))
            }
            return _pageCount
        }

    init {
        check(source.markConsumed()) { "TiffSource has already been used to open a TiffRenderer" }
        try {
            handle = TiffCoreBinding.open(source)
        } catch (t: Throwable) {
            doClose()
            throw t
        }
    }

    /** Opens [index] for rendering; see [TiffPage]. Only walks the full directory chain (via
     * [pageCount]) on a native seek failure, not on the happy path. */
    public fun openPage(index: Int): TiffPage {
        check(!closed) { "Already closed" }
        check(!pageOpen) { "Current page not closed" }
        require(index >= 0) { "Invalid page index $index: must be non-negative" }
        val h = checkNotNull(handle) { "TIFF document is not open" }
        if (_pageCount >= 0) {
            require(index < _pageCount) { "Invalid page index $index for $_pageCount pages" }
        }

        val size = try {
            TiffCoreBinding.openPage(h, index)
        } catch (e: TiffIOException) {
            // Disambiguate a bad index (IllegalArgumentException) from a corrupt in-range page.
            val resolvedPageCount = pageCount
            require(index < resolvedPageCount) { "Invalid page index $index for $resolvedPageCount pages" }
            throw e
        }
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
public class TiffPage internal constructor(
    private val owner: TiffRenderer,
    private val handle: TiffCoreHandle,
    public val index: Int,
    public val width: Int,
    public val height: Int,
) : AutoCloseable {
    private var rasterRetained = false
    private var closed = false

    /** Opts this page into caching its decoded raster so repeated [render] calls reuse it instead
     * of redecoding; released on [close]. [onPartialDecode] fires if libtiff tolerated a decode
     * error in part of the page (e.g. one bad strip); the decode still succeeds either way. */
    public fun retainRaster(onPartialDecode: (() -> Unit)? = null) {
        check(!closed) { "Already closed" }
        val partial = TiffCoreBinding.retainRaster(handle, index)
        rasterRetained = true
        if (partial) {
            onPartialDecode?.invoke()
        }
    }

    /** Renders this page into [destination]. [destClip] restricts rendering to that rect (default:
     * full destination); [transform] maps page to destination pixels (default: fit-to-clip).
     * Pixels outside the source page are left untouched. [onPartialDecode]: see [retainRaster]. */
    public fun render(
        destination: TiffBitmap,
        destClip: TiffRect? = null,
        transform: TiffTransform? = null,
        renderMode: TiffRenderMode = TiffRenderMode.FOR_DISPLAY,
        onPartialDecode: (() -> Unit)? = null,
    ) {
        check(!closed) { "Already closed" }
        if (destClip != null) {
            requireClipInBounds(destClip, destination.width, destination.height)
        }

        val clip = destClip ?: TiffRect(0, 0, destination.width, destination.height)
        val effectiveTransform = transform ?: defaultFitToClipTransform(width, height, clip)
        val partial = TiffCoreBinding.render(handle, index, destination, clip, effectiveTransform, renderMode)
        if (partial) {
            onPartialDecode?.invoke()
        }
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
