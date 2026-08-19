package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TiffRendererCodecTest {

    companion object {
        private const val RGB_PAGE_COUNT = 3
    }

    private fun open(name: String) = TiffRenderer(Fixtures.open(name))

    private fun assertDecodesAllPages(assetName: String, expectedPageCount: Int) {
        open(assetName).use { renderer ->
            assertEquals(expectedPageCount, renderer.pageCount)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                assertTrue(page.width > 0, "page $i width")
                assertTrue(page.height > 0, "page $i height")
                val bitmap = createTiffBitmap(page.width, page.height)
                page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                page.close()
            }
        }
    }

    private fun assertRejectedAtRenderNotOpen(assetName: String) {
        open(assetName).use { renderer ->
            assertEquals(RGB_PAGE_COUNT, renderer.pageCount)
            val page = renderer.openPage(0)
            assertTrue(page.width > 0)
            assertTrue(page.height > 0)
            val bitmap = createTiffBitmap(page.width, page.height)
            try {
                assertFailsWith<TiffIOException> {
                    page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                }
            } finally {
                page.close()
            }
        }
    }

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

    @Test
    fun zstd_rejectedAtRenderNotOpen() = assertRejectedAtRenderNotOpen("unsupported_zstd.tif")

    @Test
    fun lzma_rejectedAtRenderNotOpen() = assertRejectedAtRenderNotOpen("unsupported_lzma.tif")

    @Test
    fun lerc_rejectedAtRenderNotOpen() = assertRejectedAtRenderNotOpen("unsupported_lerc.tif")

    @Test
    fun varyingPageDimensions_decodesEachPageAtItsOwnSize() {
        open("varying_page_dimensions.tif").use { renderer ->
            val expectedSizes = arrayOf(10 to 10, 20 to 15, 8 to 40)
            expectedSizes.forEachIndexed { i, (w, h) ->
                val page = renderer.openPage(i)
                assertEquals(w, page.width)
                assertEquals(h, page.height)
                val bitmap = createTiffBitmap(page.width, page.height)
                page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                page.close()
            }
        }
    }

    @Test
    fun rgbaAssociatedAlpha_decodesWithoutThrowing() = assertDecodesAllPages("rgba_associated_alpha.tif", 1)
}
