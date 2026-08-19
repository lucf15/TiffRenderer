package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TiffRendererRetainRasterTest {

    private fun open(name: String) = TiffRenderer(Fixtures.open(name))

    @Test
    fun repeatedRenders_produceIdenticalOutput() {
        open("supported_lzw.tif").use { renderer ->
            val page = renderer.openPage(0)
            page.retainRaster()

            val first = createTiffBitmap(page.width, page.height)
            page.render(first, renderMode = TiffRenderMode.FOR_DISPLAY)

            val second = createTiffBitmap(page.width, page.height)
            page.render(second, renderMode = TiffRenderMode.FOR_DISPLAY)

            assertTrue(
                first.contentEquals(second),
                "repeated render() with retainRaster() must produce identical output",
            )

            page.close()
        }
    }

    @Test
    fun unsupportedCodec_rejectedByRetainRasterItself() {
        open("unsupported_zstd.tif").use { renderer ->
            val page = renderer.openPage(0)
            try {
                assertFailsWith<TiffIOException> { page.retainRaster() }
            } finally {
                page.close()
            }
        }
    }

    @Test
    fun releasedOnPageClose_doesNotLeakIntoNextPage() {
        open("varying_page_dimensions.tif").use { renderer ->
            val firstPage = renderer.openPage(0)
            firstPage.retainRaster()
            val firstBitmap = createTiffBitmap(firstPage.width, firstPage.height)
            firstPage.render(firstBitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
            firstPage.close()

            val secondPage = renderer.openPage(1)
            assertEquals(20, secondPage.width)
            assertEquals(15, secondPage.height)
            val secondBitmap = createTiffBitmap(secondPage.width, secondPage.height)
            secondPage.render(secondBitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
            secondPage.close()
        }
    }

    @Test
    fun retainRaster_afterPageClosed_throwsIllegalStateException() {
        open("single_page_rgb.tif").use { renderer ->
            val page = renderer.openPage(0)
            page.close()
            assertFailsWith<IllegalStateException> { page.retainRaster() }
        }
    }
}
