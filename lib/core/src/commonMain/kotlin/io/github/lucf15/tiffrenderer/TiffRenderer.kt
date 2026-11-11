package io.github.lucf15.tiffrenderer

import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns one document; operations are serialized and returned images outlive the document. */
public class TiffRenderer private constructor(
    private var handle: TiffCoreHandle?,
    private var source: TiffOpenedSource?,
    private val options: TiffOptions,
    private val dispatcher: CoroutineDispatcher,
) {
    private val operations = Mutex()
    private var count: Int? = null
    private var cachedPage: Int? = null

    @Throws(kotlin.coroutines.cancellation.CancellationException::class, TiffIOException::class, IllegalStateException::class)
    public suspend fun pageCount(): Int = operations.withLock { pageCountLocked(openHandle()) }

    @Throws(kotlin.coroutines.cancellation.CancellationException::class, TiffIOException::class, IllegalArgumentException::class, IllegalStateException::class)
    public suspend fun pageInfo(pageIndex: Int): TiffPageInfo = operations.withLock { pageInfoLocked(openHandle(), pageIndex) }

    @Throws(kotlin.coroutines.cancellation.CancellationException::class, TiffIOException::class, IllegalArgumentException::class, IllegalStateException::class)
    public suspend fun renderPage(pageIndex: Int, size: RenderSize, sampling: TiffSampling = TiffSampling.SMOOTH): TiffRenderResult = operations.withLock {
        val h = openHandle()
        val info = pageInfoLocked(h, pageIndex)
        val (width, height) = when (size) {
            RenderSize.Original -> info.width to info.height
            is RenderSize.Fit -> {
                val scale = min(size.maxWidth.toDouble() / info.width, size.maxHeight.toDouble() / info.height)
                    .let { if (size.allowUpscale) it else min(1.0, it) }
                (info.width * scale).roundToInt().coerceAtLeast(1) to (info.height * scale).roundToInt().coerceAtLeast(1)
            }
        }
        requireOutput(width, height)
        val bitmap = withContext(dispatcher) { createTiffBitmap(width, height) }
        val warnings = renderLocked(h, info, bitmap, TiffRect(0, 0, width, height), null, sampling)
        TiffRenderResult(bitmap, warnings)
    }

    /** In-place rendering may modify the destination before reporting a decode failure. */
    @Throws(kotlin.coroutines.cancellation.CancellationException::class, TiffIOException::class, IllegalArgumentException::class, IllegalStateException::class)
    public suspend fun renderInto(
        pageIndex: Int,
        destination: TiffBitmap,
        clip: TiffRect? = null,
        transform: TiffTransform? = null,
        sampling: TiffSampling = TiffSampling.SMOOTH,
    ): Set<TiffWarning> = operations.withLock {
        val h = openHandle()
        requireOutput(destination.width, destination.height)
        val bounds = clip ?: TiffRect(0, 0, destination.width, destination.height)
        requireClipInBounds(bounds, destination.width, destination.height)
        destination.renderMutex.withLock {
            renderLocked(h, pageInfoLocked(h, pageIndex), destination, bounds, transform, sampling)
        }
    }

    public suspend fun trimCache(): Unit = operations.withLock {
        TiffCoreBinding.releaseRaster(openHandle(), dispatcher)
        cachedPage = null
    }

    public suspend fun close(): Unit = withContext(NonCancellable) {
        operations.withLock {
            val h = handle ?: return@withLock
            try {
                TiffCoreBinding.close(h, dispatcher)
            } finally {
                handle = null
                cachedPage = null
                try { source?.close() } finally { source = null }
            }
        }
    }

    private fun openHandle(): TiffCoreHandle = checkNotNull(handle) { "TIFF document is closed" }

    private suspend fun pageCountLocked(h: TiffCoreHandle): Int = count ?: TiffCoreBinding.getPageCount(h, dispatcher).also { if (it < 0) throw TiffIOException("Cannot read TIFF directories"); count = it }

    private suspend fun pageInfoLocked(h: TiffCoreHandle, index: Int): TiffPageInfo {
        require(index >= 0) { "pageIndex must be non-negative" }
        count?.let { require(index < it) { "pageIndex $index is outside $it pages" } }
        val dimensions = try {
            TiffCoreBinding.openPage(h, index, dispatcher)
        } catch (e: TiffIOException) {
            require(index < pageCountLocked(h)) { "pageIndex $index is outside the document" }
            throw e
        }
        if (dimensions.width <= 0 || dimensions.height <= 0) throw TiffIOException("Invalid page dimensions")
        return TiffPageInfo(index, dimensions.width, dimensions.height)
    }

    private fun requireOutput(width: Int, height: Int) {
        requirePositiveNonOverflowingBitmapDimensions(width, height)
        if (width.toLong() * height * 4 > options.limits.maxOutputBytes) limit("Output bitmap exceeds maxOutputBytes")
    }

    private suspend fun renderLocked(h: TiffCoreHandle, info: TiffPageInfo, destination: TiffBitmap, clip: TiffRect, transform: TiffTransform?, sampling: TiffSampling): Set<TiffWarning> {
        if (info.rasterBytes > options.limits.maxDecodedBytes) limit("Source page exceeds maxDecodedBytes")
        if (options.cacheBytes >= info.rasterBytes && cachedPage != info.index) {
            TiffCoreBinding.releaseRaster(h, dispatcher)
            cachedPage = null
            val partial = TiffCoreBinding.retainRaster(h, info.index, dispatcher)
            if (partial && options.decodePolicy == TiffDecodePolicy.STRICT) {
                TiffCoreBinding.releaseRaster(h, dispatcher)
                throw TiffIOException("Page was only partially decoded", TiffError.PARTIAL_DECODE)
            }
            cachedPage = info.index
        }
        val partial = TiffCoreBinding.render(h, info.index, destination, clip,
            transform ?: defaultFitToClipTransform(info.width, info.height, clip), sampling, dispatcher)
        if (partial && options.decodePolicy == TiffDecodePolicy.STRICT) throw TiffIOException("Page was only partially decoded", TiffError.PARTIAL_DECODE)
        return if (partial) setOf(TiffWarning.PARTIAL_DECODE) else emptySet()
    }

    public companion object {
        @Throws(kotlin.coroutines.cancellation.CancellationException::class, TiffIOException::class, IllegalArgumentException::class)
        public suspend fun open(source: TiffSource, options: TiffOptions = TiffOptions()): TiffRenderer =
            openInternal(source, options, defaultTiffDispatcher)

        internal suspend fun open(source: TiffSource, dispatcher: CoroutineDispatcher): TiffRenderer =
            openInternal(source, TiffOptions(), dispatcher)

        public suspend fun <R> withDocument(source: TiffSource, options: TiffOptions = TiffOptions(), block: suspend (TiffRenderer) -> R): R =
            open(source, options).use(block)

        @Throws(kotlin.coroutines.cancellation.CancellationException::class, TiffIOException::class, IllegalArgumentException::class)
        public suspend fun render(source: TiffSource, pageIndex: Int, size: RenderSize, options: TiffOptions = TiffOptions()): TiffRenderResult =
            withDocument(source, options) { it.renderPage(pageIndex, size) }

        private suspend fun openInternal(input: TiffSource, options: TiffOptions, dispatcher: CoroutineDispatcher): TiffRenderer {
            var acquired: TiffOpenedSource? = null
            var opened: TiffCoreHandle? = null
            try {
                withContext(dispatcher) { input.acquire().also { acquired = it } }
                val owned = checkNotNull(acquired)
                if (owned.size < 0 || owned.size > options.limits.maxInputBytes) limit("Input exceeds maxInputBytes")
                if (owned.inMemory && owned.size > (options.limits.maxWorkingBytes - NATIVE_RESERVE_BYTES) / 2) limit("Input staging exceeds maxWorkingBytes")
                opened = TiffCoreBinding.open(owned, dispatcher)
                TiffCoreBinding.setLimits(opened, options.limits, dispatcher)
                currentCoroutineContext().ensureActive()
                return TiffRenderer(opened, owned, options, dispatcher)
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    try { opened?.let { TiffCoreBinding.close(it, dispatcher) } }
                    catch (cleanup: Throwable) { t.addSuppressed(cleanup) }
                    try { acquired?.close() } catch (cleanup: Throwable) { t.addSuppressed(cleanup) }
                }
                throw t
            }
        }
    }
}

internal const val NATIVE_RESERVE_BYTES: Long = 32L * 1024 * 1024
private fun limit(message: String): Nothing = throw TiffIOException(message, TiffError.RESOURCE_LIMIT)
