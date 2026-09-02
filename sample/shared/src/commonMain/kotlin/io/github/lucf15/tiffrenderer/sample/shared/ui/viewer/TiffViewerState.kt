package io.github.lucf15.tiffrenderer.sample.shared.ui.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import io.github.lucf15.tiffrenderer.TiffRenderMode
import io.github.lucf15.tiffrenderer.TiffRenderer
import io.github.lucf15.tiffrenderer.TiffSource
import io.github.lucf15.tiffrenderer.createTiffBitmap
import io.github.lucf15.tiffrenderer.sample.shared.toImageBitmap
import io.github.lucf15.tiffrenderer.use
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns a [TiffRenderer] for one opened document, opened lazily on first use (construction itself
 * can't suspend). Plain class, not `ViewModel`, since this sample doesn't need process-death
 * survival. Every method serializes on [pageLock]: [TiffRenderer] only allows one page open at a
 * time, and a scrolling list can call these concurrently. No explicit dispatcher here: every
 * `TiffRenderer`/[TiffCoreBinding] call is already main-safe (it dispatches to
 * `Dispatchers.Default` internally), so this class is safe to drive directly from
 * `Dispatchers.Main`/`viewModelScope` without its own `withContext`. */
class TiffViewerState(private val source: TiffSource) {
    private val pageLock = Mutex()
    private var renderer: TiffRenderer? = null

    private suspend fun rendererOrOpen(): TiffRenderer =
        renderer ?: TiffRenderer.open(source).also { renderer = it }

    suspend fun pageSizes(): List<IntSize> = pageLock.withLock {
        val r = rendererOrOpen()
        (0 until r.pageCount()).map { index ->
            r.openPage(index).use { page -> IntSize(page.width, page.height) }
        }
    }

    /** Renders into a [targetWidth]x[targetHeight] bitmap, not the page's native resolution, so a
     * large scan never decodes at full size just to be shown shrunk on-screen: the library's own
     * mip-pyramid downscaling does that as part of the decode. */
    suspend fun renderPage(index: Int, targetWidth: Int, targetHeight: Int): ImageBitmap = pageLock.withLock {
        rendererOrOpen().openPage(index).use { page ->
            val bitmap = createTiffBitmap(targetWidth, targetHeight)
            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
            bitmap.toImageBitmap()
        }
    }

    suspend fun close() = pageLock.withLock { renderer?.close() ?: source.release() }
}
