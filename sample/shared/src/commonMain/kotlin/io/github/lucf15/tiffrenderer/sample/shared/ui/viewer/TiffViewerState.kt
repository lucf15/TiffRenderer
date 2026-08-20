package io.github.lucf15.tiffrenderer.sample.shared.ui.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import io.github.lucf15.tiffrenderer.TiffRenderMode
import io.github.lucf15.tiffrenderer.TiffRenderer
import io.github.lucf15.tiffrenderer.TiffSource
import io.github.lucf15.tiffrenderer.createTiffBitmap
import io.github.lucf15.tiffrenderer.sample.shared.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns a [TiffRenderer] for one opened document. Plain class, not `ViewModel`, since this sample
 * doesn't need process-death survival. Every method serializes on [pageLock]: [TiffRenderer] only
 * allows one page open at a time, and a scrolling list can call these concurrently. */
class TiffViewerState(source: TiffSource) {
    private val pageLock = Mutex()
    private val renderer = TiffRenderer(source)

    val pageCount: Int get() = renderer.pageCount

    suspend fun pageSizes(): List<IntSize> =
        withContext(Dispatchers.Default) {
            pageLock.withLock {
                (0 until pageCount).map { index ->
                    renderer.openPage(index).use { page -> IntSize(page.width, page.height) }
                }
            }
        }

    /** Renders into a [targetWidth]x[targetHeight] bitmap, not the page's native resolution, so a
     * large scan never decodes at full size just to be shown shrunk on-screen: the library's own
     * mip-pyramid downscaling does that as part of the decode. */
    suspend fun renderPage(index: Int, targetWidth: Int, targetHeight: Int): ImageBitmap =
        withContext(Dispatchers.Default) {
            pageLock.withLock {
                renderer.openPage(index).use { page ->
                    val bitmap = createTiffBitmap(targetWidth, targetHeight)
                    page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                    bitmap.toImageBitmap()
                }
            }
        }

    suspend fun close() =
        withContext(Dispatchers.Default) {
            pageLock.withLock { renderer.close() }
        }
}
