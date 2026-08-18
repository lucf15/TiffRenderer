package io.github.lucf15.tiffrenderer

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The codec support matrix from CMakeLists.txt: supported codecs must fully decode; unsupported
 * ones must fail specifically from render(), never openPage(). */
class TiffRendererCodecTest {

    companion object {
        private const val RGB_PAGE_COUNT = 3
    }

    private fun open(name: String) =
        TiffRenderer(TiffSource.fromParcelFileDescriptor(TestFixtures.open(name)))

    private fun assertDecodesAllPages(assetName: String, expectedPageCount: Int) {
        open(assetName).use { renderer ->
            assertEquals(expectedPageCount, renderer.pageCount)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                assertTrue("page $i width", page.width > 0)
                assertTrue("page $i height", page.height > 0)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(TiffBitmap(bitmap), renderMode = TiffRenderMode.FOR_DISPLAY)
                bitmap.recycle()
                page.close()
            }
        }
    }

    /** An unsupported codec only fails once render() actually invokes it: open/getPageCount
     * succeed regardless. */
    private fun assertRejectedAtRenderNotOpen(assetName: String) {
        open(assetName).use { renderer ->
            assertEquals(RGB_PAGE_COUNT, renderer.pageCount)
            val page = renderer.openPage(0) // must not throw
            assertTrue(page.width > 0)
            assertTrue(page.height > 0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            try {
                assertThrows(TiffIOException::class.java) {
                    page.render(TiffBitmap(bitmap), renderMode = TiffRenderMode.FOR_DISPLAY)
                }
            } finally {
                bitmap.recycle()
                page.close()
            }
        }
    }

    // --- Supported (built into libtiff itself, or built against the NDK's bundled zlib) -------

    @Test
    fun uncompressed_decodesAllPages() = assertDecodesAllPages("supported_uncompressed.tif", RGB_PAGE_COUNT)

    @Test
    fun lzw_decodesAllPages() = assertDecodesAllPages("supported_lzw.tif", RGB_PAGE_COUNT)

    @Test
    fun packBits_decodesAllPages() = assertDecodesAllPages("supported_packbits.tif", RGB_PAGE_COUNT)

    @Test
    fun deflate_decodesAllPages() = assertDecodesAllPages("supported_deflate.tif", RGB_PAGE_COUNT)

    @Test
    fun ccittGroup4_decodesAllPages() = assertDecodesAllPages("supported_ccittg4.tif", 1)

    @Test
    fun jpeg_decodesAllPages() = assertDecodesAllPages("supported_jpeg.tif", RGB_PAGE_COUNT)

    @Test
    fun webp_decodesAllPages() = assertDecodesAllPages("supported_webp.tif", RGB_PAGE_COUNT)

    // --- Unsupported (codec disabled at build time, see CMakeLists.txt) ---------------------

    @Test
    fun zstd_rejectedAtRenderNotOpen() = assertRejectedAtRenderNotOpen("unsupported_zstd.tif")

    @Test
    fun lzma_rejectedAtRenderNotOpen() = assertRejectedAtRenderNotOpen("unsupported_lzma.tif")

    @Test
    fun lerc_rejectedAtRenderNotOpen() = assertRejectedAtRenderNotOpen("unsupported_lerc.tif")

    // --- Non-uniform layouts ---------------------------------------------------------------

    /** Each page has a *different* ImageWidth/ImageLength: catches bugs assuming uniform size. */
    @Test
    fun varyingPageDimensions_decodesEachPageAtItsOwnSize() {
        open("varying_page_dimensions.tif").use { renderer ->
            val expectedSizes = arrayOf(10 to 10, 20 to 15, 8 to 40)
            expectedSizes.forEachIndexed { i, (w, h) ->
                val page = renderer.openPage(i)
                assertEquals(w, page.width)
                assertEquals(h, page.height)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(TiffBitmap(bitmap), renderMode = TiffRenderMode.FOR_DISPLAY)
                bitmap.recycle()
                page.close()
            }
        }
    }

    /** RGB + an associated-alpha extra sample: exercises SamplesPerPixel=4 decoding. */
    @Test
    fun rgbaAssociatedAlpha_decodesWithoutThrowing() = assertDecodesAllPages("rgba_associated_alpha.tif", 1)
}
