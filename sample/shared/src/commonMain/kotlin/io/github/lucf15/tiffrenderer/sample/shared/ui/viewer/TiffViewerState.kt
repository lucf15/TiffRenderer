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
 * [renderPage] serializes page access under [pageLock], since [TiffRenderer] only allows one page
 * open at a time and a scrolling multi-page list calls this concurrently as items enter
 * composition — unlike `TiffPageImage`, which holds a single already-open page for its
 * composable's lifetime and has nothing to coordinate. */
class TiffViewerState(source: TiffSource) {
    private val pageLock = Mutex()
    private val renderer = TiffRenderer(source)

    val pageCount: Int get() = renderer.pageCount

    suspend fun pageSizes(): List<IntSize> =
        withContext(Dispatchers.Default) {
            (0 until pageCount).map { index ->
                renderer.openPage(index).use { page -> IntSize(page.width, page.height) }
            }
        }

    suspend fun renderPage(index: Int): ImageBitmap =
        withContext(Dispatchers.Default) {
            pageLock.withLock {
                renderer.openPage(index).use { page ->
                    val bitmap = createTiffBitmap(page.width, page.height)
                    page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                    bitmap.toImageBitmap()
                }
            }
        }

    fun close() = renderer.close()
}
