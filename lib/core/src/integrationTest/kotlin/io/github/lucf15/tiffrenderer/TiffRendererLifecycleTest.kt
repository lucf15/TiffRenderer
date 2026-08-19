package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TiffRendererLifecycleTest {

    private fun open(name: String) = TiffRenderer(Fixtures.open(name))

    @Test
    fun constructor_notATiffFile_throwsTiffIOException() {
        assertFailsWith<TiffIOException> { open("not_a_tiff.bin") }
    }

    @Test
    fun constructor_validFile_reportsCorrectPageCount() {
        open("varying_page_dimensions.tif").use { renderer ->
            assertEquals(3, renderer.pageCount)
        }
    }

    @Test
    fun getPageCount_afterClose_throwsIllegalStateException() {
        val renderer = open("single_page_rgb.tif")
        renderer.close()
        assertFailsWith<IllegalStateException> { renderer.pageCount }
    }

    @Test
    fun close_calledTwice_throwsIllegalStateException() {
        val renderer = open("single_page_rgb.tif")
        renderer.close()
        assertFailsWith<IllegalStateException> { renderer.close() }
    }

    @Test
    fun close_whilePageOpen_throwsIllegalStateException() {
        val renderer = open("single_page_rgb.tif")
        val page = renderer.openPage(0)
        assertFailsWith<IllegalStateException> { renderer.close() }
        page.close()
        renderer.close()
    }

    @Test
    fun openPage_afterClose_throwsIllegalStateException() {
        val renderer = open("single_page_rgb.tif")
        renderer.close()
        assertFailsWith<IllegalStateException> { renderer.openPage(0) }
    }

    @Test
    fun openPage_negativeIndex_throwsIllegalArgumentException() {
        open("single_page_rgb.tif").use { renderer ->
            assertFailsWith<IllegalArgumentException> { renderer.openPage(-1) }
        }
    }

    @Test
    fun openPage_indexAtOrPastPageCount_throwsIllegalArgumentException() {
        open("single_page_rgb.tif").use { renderer ->
            assertEquals(1, renderer.pageCount)
            assertFailsWith<IllegalArgumentException> { renderer.openPage(1) }
        }
    }

    @Test
    fun openPage_whileAnotherPageOpen_throwsIllegalStateException() {
        open("varying_page_dimensions.tif").use { renderer ->
            val page = renderer.openPage(0)
            assertFailsWith<IllegalStateException> { renderer.openPage(1) }
            page.close()
        }
    }

    @Test
    fun openPage_afterPreviousPageClosed_succeeds() {
        open("varying_page_dimensions.tif").use { renderer ->
            renderer.openPage(0).close()
            val second = renderer.openPage(1)
            assertEquals(1, second.index)
            second.close()
        }
    }

    @Test
    fun page_close_calledTwice_throwsIllegalStateException() {
        open("single_page_rgb.tif").use { renderer ->
            val page = renderer.openPage(0)
            page.close()
            assertFailsWith<IllegalStateException> { page.close() }
        }
    }

    @Test
    fun constructor_sourceAlreadyConsumedBySuccessfulOpen_throwsIllegalStateException() {
        val source = Fixtures.open("single_page_rgb.tif")
        TiffRenderer(source).use { }
        assertFailsWith<IllegalStateException> { TiffRenderer(source) }
    }

    @Test
    fun constructor_sourceAlreadyConsumedByFailedOpen_throwsIllegalStateExceptionOnRetry() {
        val source = Fixtures.open("not_a_tiff.bin")
        assertFailsWith<TiffIOException> { TiffRenderer(source) }
        assertFailsWith<IllegalStateException> { TiffRenderer(source) }
    }

    @Test
    fun page_indexWidthHeight_matchPerPageTiffTags() {
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
