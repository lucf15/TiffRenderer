package io.github.lucf15.tiffrenderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [TiffRenderer]/[TiffPage]'s state machine: construction, open/close preconditions,
 * one-page-open-at-a-time. No `constructor_nullInput_throwsNullPointerException` test:
 * [TiffRenderer]'s constructor takes a non-null [TiffSource], so passing `null` is a Kotlin
 * compile error rather than a runtime check.
 */
class TiffRendererLifecycleTest {

    private fun open(name: String) = TiffSource.fromParcelFileDescriptor(TestFixtures.open(name))

    @Test
    fun constructor_notATiffFile_throwsTiffIOException() {
        assertThrows(TiffIOException::class.java) { TiffRenderer(open("not_a_tiff.bin")) }
    }

    @Test
    fun constructor_validFile_reportsCorrectPageCount() {
        TiffRenderer(open("varying_page_dimensions.tif")).use { renderer ->
            assertEquals(3, renderer.pageCount)
        }
    }

    @Test
    fun getPageCount_afterClose_throwsIllegalStateException() {
        val renderer = TiffRenderer(open("single_page_rgb.tif"))
        renderer.close()
        assertThrows(IllegalStateException::class.java) { renderer.pageCount }
    }

    @Test
    fun close_calledTwice_throwsIllegalStateException() {
        val renderer = TiffRenderer(open("single_page_rgb.tif"))
        renderer.close()
        assertThrows(IllegalStateException::class.java) { renderer.close() }
    }

    @Test
    fun close_whilePageOpen_throwsIllegalStateException() {
        val renderer = TiffRenderer(open("single_page_rgb.tif"))
        val page = renderer.openPage(0)
        assertThrows(IllegalStateException::class.java) { renderer.close() }
        page.close()
        renderer.close()
    }

    @Test
    fun openPage_afterClose_throwsIllegalStateException() {
        val renderer = TiffRenderer(open("single_page_rgb.tif"))
        renderer.close()
        assertThrows(IllegalStateException::class.java) { renderer.openPage(0) }
    }

    @Test
    fun openPage_negativeIndex_throwsIllegalArgumentException() {
        TiffRenderer(open("single_page_rgb.tif")).use { renderer ->
            assertThrows(IllegalArgumentException::class.java) { renderer.openPage(-1) }
        }
    }

    @Test
    fun openPage_indexAtOrPastPageCount_throwsIllegalArgumentException() {
        TiffRenderer(open("single_page_rgb.tif")).use { renderer ->
            assertEquals(1, renderer.pageCount)
            assertThrows(IllegalArgumentException::class.java) { renderer.openPage(1) }
        }
    }

    @Test
    fun openPage_whileAnotherPageOpen_throwsIllegalStateException() {
        TiffRenderer(open("varying_page_dimensions.tif")).use { renderer ->
            val page = renderer.openPage(0)
            assertThrows(IllegalStateException::class.java) { renderer.openPage(1) }
            page.close()
        }
    }

    @Test
    fun openPage_afterPreviousPageClosed_succeeds() {
        TiffRenderer(open("varying_page_dimensions.tif")).use { renderer ->
            renderer.openPage(0).close()
            val second = renderer.openPage(1)
            assertEquals(1, second.index)
            second.close()
        }
    }

    @Test
    fun page_close_calledTwice_throwsIllegalStateException() {
        TiffRenderer(open("single_page_rgb.tif")).use { renderer ->
            val page = renderer.openPage(0)
            page.close()
            assertThrows(IllegalStateException::class.java) { page.close() }
        }
    }

    @Test
    fun page_indexWidthHeight_matchPerPageTiffTags() {
        TiffRenderer(open("varying_page_dimensions.tif")).use { renderer ->
            val expectedSizes = arrayOf(10 to 10, 20 to 15, 8 to 40)
            expectedSizes.forEachIndexed { i, (w, h) ->
                val page = renderer.openPage(i)
                assertEquals(i, page.index)
                assertEquals("page $i width", w, page.width)
                assertEquals("page $i height", h, page.height)
                page.close()
            }
        }
    }

    // No renderer_finalizeWithoutClose_doesNotThrow equivalent: Kotlin's finalize() support is
    // deprecated/unreliable, so there's no leak-warning path to test; an unreferenced
    // TiffRenderer/TiffPage just leaks native state silently unless the caller calls close().
}
