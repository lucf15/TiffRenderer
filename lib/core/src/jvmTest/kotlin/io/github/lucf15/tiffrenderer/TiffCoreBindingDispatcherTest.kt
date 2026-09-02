package io.github.lucf15.tiffrenderer

import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/** Nothing else in this suite overrides [TiffRenderer.open]'s `dispatcher` parameter (every other
 * test relies on its [Dispatchers.Default] default), so nothing would catch a regression that
 * silently stopped honoring it. This proves the parameter is actually load-bearing. */
class TiffCoreBindingDispatcherTest {
    private class RecordingDispatcher(private val delegate: CoroutineDispatcher = Dispatchers.Default) : CoroutineDispatcher() {
        var dispatchCount = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount++
            delegate.dispatch(context, block)
        }
    }

    @Test
    fun open_usesTheInjectedDispatcherForEveryCall() = runTest {
        val dispatcher = RecordingDispatcher()
        val renderer = TiffRenderer.open(Fixtures.open("single_page_rgb.tif"), dispatcher)
        val afterOpen = dispatcher.dispatchCount
        assertTrue(afterOpen > 0, "open() should dispatch through the injected dispatcher")

        renderer.pageCount()
        val page = renderer.openPage(0)
        page.render(createTiffBitmap(page.width, page.height))
        page.close()
        renderer.close()

        assertTrue(dispatcher.dispatchCount > afterOpen, "later calls should keep using the injected dispatcher")
    }
}
