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

/** Owns a [TiffRenderer] for one opened document. Plain class, not `ViewModel`: this sample
 * doesn't need process-death survival, so a Compose-owned `remember` is enough.
 *
 * Every method serializes on [pageLock], since [TiffRenderer] only allows one page open at a time
 * and a scrolling multi-page list can call [renderPage]/[pageSizes]/[close] concurrently as items
 * enter and leave composition. */
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

    /** Renders into a [targetWidth]x[targetHeight] bitmap, not the page's own native resolution —
     * the caller passes the actual on-screen display size, so a large scanned page never gets
     * decoded at, say, 10000x14000 just to be shown shrunk to a few hundred on-screen pixels. The
     * library's own mip-pyramid downscaling (see `TiffRendererMinificationTest`) does the size
     * reduction as part of the decode, not as a separate post-decode resize. */
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
