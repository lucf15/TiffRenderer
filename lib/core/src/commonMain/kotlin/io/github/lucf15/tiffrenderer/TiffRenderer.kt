package io.github.lucf15.tiffrenderer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Decodes and rasterizes TIFF documents page by page; mirrors Android's `PdfRenderer` (same
 * method names, lifecycle, and page/render-mode pattern), except fully `suspend`-based: wasmJs
 * genuinely needs every call to suspend so it can offload decoding to a Web Worker instead of
 * blocking the browser's single UI thread (there's no real background-thread dispatcher there the
 * way there is on every other platform). On every other platform, every call is already safe to
 * make from `Dispatchers.Main`/`viewModelScope` without the caller adding their own
 * `withContext`: internally it dispatches to [dispatcher] (default [Dispatchers.Default], the
 * right choice for this CPU-bound decode work, not [Dispatchers.IO]), which [open] also accepts
 * as a parameter rather than hardcoding, so a test can substitute its own. Concurrent
 * [TiffPage.render] calls through the same page are memory-safe; the lifecycle (`openPage`/
 * `close`) is not. Not [AutoCloseable] since that interface has no `suspend close()`; use
 * [TiffRenderer.use] instead of `.use {}`. */
public class TiffRenderer private constructor(
    private var handle: TiffCoreHandle?,
    private var openSource: TiffSource?,
    private val dispatcher: CoroutineDispatcher,
) {
    // Guards pageOpen/currentPage across openPage()/close()/TiffPage.close() so two concurrent
    // openPage() calls can't both pass the check before either flips pageOpen. Internal, not
    // private: TiffPage.close() takes it too, to serialize against its owning renderer.
    internal val stateMutex = Mutex()

    // -1 means "not yet resolved"; see pageCount's KDoc below for why.
    private var _pageCount: Int = -1
    private var currentPage: TiffPage? = null
    private var closed = false
    private var pageOpen = false

    /** Number of pages in this document. Unlike PDF's `/Count`, TIFF has no page-count field: this
     * walks the whole IFD chain, so it's resolved lazily on first access and cached. */
    public suspend fun pageCount(): Int {
        check(!closed) { "Already closed" }
        if (_pageCount < 0) {
            _pageCount = TiffCoreBinding.getPageCount(checkNotNull(handle), dispatcher)
        }
        return _pageCount
    }

    /** Opens [index] for rendering; see [TiffPage]. Only walks the full directory chain (via
     * [pageCount]) on a native seek failure, not on the happy path. */
    public suspend fun openPage(index: Int): TiffPage = stateMutex.withLock {
        check(!closed) { "Already closed" }
        check(!pageOpen) { "Current page not closed" }
        require(index >= 0) { "Invalid page index $index: must be non-negative" }
        val h = checkNotNull(handle) { "TIFF document is not open" }
        if (_pageCount >= 0) {
            require(index < _pageCount) { "Invalid page index $index for $_pageCount pages" }
        }

        val size = try {
            TiffCoreBinding.openPage(h, index, dispatcher)
        } catch (e: TiffIOException) {
            // Disambiguate a bad index (IllegalArgumentException) from a corrupt in-range page.
            val resolvedPageCount = pageCount()
            require(index < resolvedPageCount) { "Invalid page index $index for $resolvedPageCount pages" }
            throw e
        }
        val page = TiffPage(this, h, index, size.width, size.height, dispatcher)
        currentPage = page
        pageOpen = true
        page
    }

    public suspend fun close(): Unit = stateMutex.withLock {
        check(!closed) { "Already closed" }
        check(!pageOpen) { "Current page not closed" }
        doClose()
    }

    internal fun onPageClosed() {
        currentPage = null
        pageOpen = false
    }

    private suspend fun doClose() {
        try {
            currentPage?.let {
                it.closeInternal()
                currentPage = null
            }
            handle?.let { TiffCoreBinding.close(it, dispatcher) }
        } finally {
            handle = null
            openSource?.release()
            openSource = null
            closed = true
        }
    }

    public companion object {
        public suspend fun open(source: TiffSource, dispatcher: CoroutineDispatcher = defaultTiffDispatcher): TiffRenderer {
            check(source.markConsumed()) { "TiffSource has already been used to open a TiffRenderer" }
            val handle = try {
                TiffCoreBinding.open(source, dispatcher)
            } catch (t: Throwable) {
                source.release()
                throw t
            }
            return TiffRenderer(handle, source, dispatcher)
        }
    }
}

/** A TIFF document page (directory) for rendering; see [TiffRenderer.openPage]. */
public class TiffPage internal constructor(
    private val owner: TiffRenderer,
    private val handle: TiffCoreHandle,
    public val index: Int,
    public val width: Int,
    public val height: Int,
    private val dispatcher: CoroutineDispatcher,
) {
    private var rasterRetained = false
    private var closed = false

    /** Opts this page into caching its decoded raster so repeated [render] calls reuse it instead
     * of redecoding; released on [close]. [onPartialDecode] fires if libtiff tolerated a decode
     * error in part of the page (e.g. one bad strip); the decode still succeeds either way. */
    public suspend fun retainRaster(onPartialDecode: (() -> Unit)? = null) {
        check(!closed) { "Already closed" }
        val partial = TiffCoreBinding.retainRaster(handle, index, dispatcher)
        rasterRetained = true
        if (partial) {
            onPartialDecode?.invoke()
        }
    }

    /** Renders this page into [destination]. [destClip] restricts rendering to that rect (default:
     * full destination); [transform] maps page to destination pixels (default: fit-to-clip).
     * Pixels outside the source page are left untouched. [onPartialDecode]: see [retainRaster]. */
    public suspend fun render(
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
        val partial =
            TiffCoreBinding.render(handle, index, destination, clip, effectiveTransform, renderMode, dispatcher)
        if (partial) {
            onPartialDecode?.invoke()
        }
    }

    public suspend fun close(): Unit = owner.stateMutex.withLock {
        check(!closed) { "Already closed" }
        closeInternal()
    }

    internal suspend fun closeInternal() {
        if (rasterRetained) {
            TiffCoreBinding.releaseRaster(handle, dispatcher)
            rasterRetained = false
        }
        closed = true
        owner.onPageClosed()
    }
}
