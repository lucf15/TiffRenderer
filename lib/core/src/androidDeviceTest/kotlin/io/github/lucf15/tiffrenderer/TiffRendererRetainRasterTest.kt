package io.github.lucf15.tiffrenderer

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TiffPage.retainRaster]'s decode-once, render-many opt-in caching. */
class TiffRendererRetainRasterTest {

    private fun open(name: String) =
        TiffRenderer(TiffSource.fromParcelFileDescriptor(TestFixtures.open(name)))

    /** Repeated render() calls against the same open page must keep producing correct output. */
    @Test
    fun repeatedRenders_produceIdenticalOutput() {
        open("supported_lzw.tif").use { renderer ->
            val page = renderer.openPage(0)
            page.retainRaster()

            val first = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(TiffBitmap(first), renderMode = TiffRenderMode.FOR_DISPLAY)

            val second = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(TiffBitmap(second), renderMode = TiffRenderMode.FOR_DISPLAY)

            assertTrue(
                "repeated render() with retainRaster() must produce identical output",
                first.sameAs(second),
            )

            first.recycle()
            second.recycle()
            page.close()
        }
    }

    /** A codec this build can't decode must fail loudly from retainRaster() itself, not the next
     * render(). */
    @Test
    fun unsupportedCodec_rejectedByRetainRasterItself() {
        open("unsupported_zstd.tif").use { renderer ->
            val page = renderer.openPage(0)
            try {
                assertThrows(TiffIOException::class.java) { page.retainRaster() }
            } finally {
                page.close()
            }
        }
    }

    /** Closing a page that opted into retainRaster() must not leak the cache into the next page. */
    @Test
    fun releasedOnPageClose_doesNotLeakIntoNextPage() {
        open("varying_page_dimensions.tif").use { renderer ->
            val firstPage = renderer.openPage(0) // 10x10
            firstPage.retainRaster()
            val firstBitmap = Bitmap.createBitmap(firstPage.width, firstPage.height, Bitmap.Config.ARGB_8888)
            firstPage.render(TiffBitmap(firstBitmap), renderMode = TiffRenderMode.FOR_DISPLAY)
            firstBitmap.recycle()
            firstPage.close()

            // Page 2 is a different size (20x15 vs 10x10): a leaked cache would crash or misdecode here.
            val secondPage = renderer.openPage(1)
            assertEquals(20, secondPage.width)
            assertEquals(15, secondPage.height)
            val secondBitmap = Bitmap.createBitmap(secondPage.width, secondPage.height, Bitmap.Config.ARGB_8888)
            secondPage.render(TiffBitmap(secondBitmap), renderMode = TiffRenderMode.FOR_DISPLAY)
            secondBitmap.recycle()
            secondPage.close()
        }
    }

    @Test
    fun retainRaster_afterPageClosed_throwsIllegalStateException() {
        open("single_page_rgb.tif").use { renderer ->
            val page = renderer.openPage(0)
            page.close()
            assertThrows(IllegalStateException::class.java) { page.retainRaster() }
        }
    }
}
