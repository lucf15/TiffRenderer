package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class TiffRendererLifecycleTest {

    private suspend fun open(name: String) = TiffRenderer.open(Fixtures.open(name), Dispatchers.Unconfined)

    @Test
    fun constructor_notATiffFile_throwsTiffIOException() = runTest {
        assertFailsWith<TiffIOException> { open("not_a_tiff.bin") }
    }

    @Test
    fun constructor_validFile_reportsCorrectPageCount() = runTest {
        open("varying_page_dimensions.tif").use { renderer ->
            assertEquals(3, renderer.pageCount())
        }
    }

    @Test
    fun getPageCount_afterClose_throwsIllegalStateException() = runTest {
        val renderer = open("single_page_rgb.tif")
        renderer.close()
        assertFailsWith<IllegalStateException> { renderer.pageCount() }
    }

    @Test
    fun close_calledTwice_throwsIllegalStateException() = runTest {
        val renderer = open("single_page_rgb.tif")
        renderer.close()
        assertFailsWith<IllegalStateException> { renderer.close() }
    }

    @Test
    fun close_whilePageOpen_throwsIllegalStateException() = runTest {
        val renderer = open("single_page_rgb.tif")
        val page = renderer.openPage(0)
        assertFailsWith<IllegalStateException> { renderer.close() }
        page.close()
        renderer.close()
    }

    @Test
    fun openPage_afterClose_throwsIllegalStateException() = runTest {
        val renderer = open("single_page_rgb.tif")
        renderer.close()
        assertFailsWith<IllegalStateException> { renderer.openPage(0) }
    }

    @Test
    fun openPage_negativeIndex_throwsIllegalArgumentException() = runTest {
        open("single_page_rgb.tif").use { renderer ->
            assertFailsWith<IllegalArgumentException> { renderer.openPage(-1) }
        }
    }

    @Test
    fun openPage_indexAtOrPastPageCount_throwsIllegalArgumentException() = runTest {
        open("single_page_rgb.tif").use { renderer ->
            assertEquals(1, renderer.pageCount())
            assertFailsWith<IllegalArgumentException> { renderer.openPage(1) }
        }
    }

    @Test
    fun openPage_indexPastPageCount_withoutPageCountQueriedFirst_stillThrowsAndRendererRecovers() = runTest {
        // Deliberately doesn't touch renderer.pageCount() first, so _pageCount is still the -1
        // sentinel when openPage runs: exercises the fallback path that walks the full directory
        // chain only after TiffCoreBinding.openPage's native seek fails, not the fast path where
        // the bound is already cached.
        open("varying_page_dimensions.tif").use { renderer ->
            assertFailsWith<IllegalArgumentException> { renderer.openPage(5) }
            // Proves the underlying TIFF* survives a failed TIFFSetDirectory followed by the
            // TIFFNumberOfDirectories walk: this renderer is still fully usable afterward.
            val page = renderer.openPage(0)
            assertEquals(0, page.index)
            page.close()
        }
    }

    @Test
    fun openPage_whileAnotherPageOpen_throwsIllegalStateException() = runTest {
        open("varying_page_dimensions.tif").use { renderer ->
            val page = renderer.openPage(0)
            assertFailsWith<IllegalStateException> { renderer.openPage(1) }
            page.close()
        }
    }

    @Test
    fun openPage_afterPreviousPageClosed_succeeds() = runTest {
        open("varying_page_dimensions.tif").use { renderer ->
            renderer.openPage(0).close()
            val second = renderer.openPage(1)
            assertEquals(1, second.index)
            second.close()
        }
    }

    @Test
    fun page_close_calledTwice_throwsIllegalStateException() = runTest {
        open("single_page_rgb.tif").use { renderer ->
            val page = renderer.openPage(0)
            page.close()
            assertFailsWith<IllegalStateException> { page.close() }
        }
    }

    @Test
    fun constructor_sourceAlreadyConsumedBySuccessfulOpen_throwsIllegalStateException() = runTest {
        val source = Fixtures.open("single_page_rgb.tif")
        TiffRenderer.open(source).use { }
        assertFailsWith<IllegalStateException> { TiffRenderer.open(source) }
    }

    @Test
    fun constructor_sourceAlreadyConsumedByFailedOpen_throwsIllegalStateExceptionOnRetry() = runTest {
        val source = Fixtures.open("not_a_tiff.bin")
        assertFailsWith<TiffIOException> { TiffRenderer.open(source) }
        assertFailsWith<IllegalStateException> { TiffRenderer.open(source) }
    }

    @Test
    fun page_indexWidthHeight_matchPerPageTiffTags() = runTest {
        open("varying_page_dimensions.tif").use { renderer ->
            val expectedSizes = arrayOf(10 to 10, 20 to 15, 8 to 40)
            expectedSizes.forEachIndexed { i, (w, h) ->
                val page = renderer.openPage(i)
                assertEquals(i, page.index)
                assertEquals(w, page.width, "page $i width")
                assertEquals(h, page.height, "page $i height")
                page.close()
            }
        }
    }
}
