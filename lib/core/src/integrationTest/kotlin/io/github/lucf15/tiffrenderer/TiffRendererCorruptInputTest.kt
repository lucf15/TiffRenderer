package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class TiffRendererCorruptInputTest {

    private suspend fun open(name: String) = TiffRenderer.open(Fixtures.open(name), Dispatchers.Unconfined)

    @Test
    fun constructor_garbageBytes_throwsTiffIOException() = runTest {
        assertFailsWith<TiffIOException> { open("not_a_tiff.bin") }
    }

    @Test
    fun render_hugeDimensionsPage_throwsTiffIOExceptionInsteadOfCrashing() = runTest {
        open("huge_dimensions.tif").use { renderer ->
            val page = renderer.openPage(0)
            assertEquals(100000, page.width)
            assertEquals(100000, page.height)

            val bitmap = createTiffBitmap(4, 4)
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
    fun retainRaster_hugeDimensionsPage_throwsTiffIOExceptionInsteadOfCrashing() = runTest {
        open("huge_dimensions.tif").use { renderer ->
            val page = renderer.openPage(0)
            try {
                assertFailsWith<TiffIOException> { page.retainRaster() }
            } finally {
                page.close()
            }
        }
    }

    @Test
    fun render_truncatedFile_throwsTiffIOException() = runTest {
        open("truncated.tif").use { renderer ->
            val page = renderer.openPage(0)
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

    /** `partially_corrupt.tif`: a 6-strip LZW image with one middle strip's bytes flipped, corrupt
     * enough to hit an invalid LZW code but not to fail the read outright. libtiff tolerates it
     * (`stopOnError=0`) and returns the rest of the raster, so this must succeed, unlike
     * [render_truncatedFile_throwsTiffIOException] above where the read fails outright. */
    @Test
    fun render_partiallyCorruptFile_succeedsWithTheDecodableStrips() = runTest {
        open("partially_corrupt.tif").use { renderer ->
            val page = renderer.openPage(0)
            assertEquals(32, page.width)
            assertEquals(48, page.height)
            val bitmap = createTiffBitmap(page.width, page.height)
            var partialDecodeCallbacks = 0
            try {
                // FOR_PRINT (nearest-neighbor) at 1:1 scale so each row maps to its own source row,
                // with no bilinear blending across the strip boundaries being asserted below.
                page.render(bitmap, renderMode = TiffRenderMode.FOR_PRINT) { partialDecodeCallbacks++ }
            } finally {
                page.close()
            }

            assertEquals(1, partialDecodeCallbacks)

            // Strips 0, 1, 2, 4, 5 (rows 0-23 and 32-47) decoded cleanly; only strip 3 (rows 24-31)
            // contains the corrupted bytes, so it's the only one not asserted here.
            assertEquals(argb(255, 255, 0, 0), bitmap.pixelAt(0, 0))
            assertEquals(argb(255, 0, 255, 0), bitmap.pixelAt(0, 8))
            assertEquals(argb(255, 0, 0, 255), bitmap.pixelAt(0, 16))
            assertEquals(argb(255, 0, 255, 255), bitmap.pixelAt(0, 32))
            assertEquals(argb(255, 255, 0, 255), bitmap.pixelAt(0, 40))
        }
    }

    /** Same fixture, but via [TiffPage.retainRaster] first: proves the cached-raster render path
     * (a separate code path from the direct-decode one above) also reports partial-ness. */
    @Test
    fun retainRasterThenRender_partiallyCorruptFile_succeedsAndReportsPartialFromBothCalls() = runTest {
        open("partially_corrupt.tif").use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = createTiffBitmap(page.width, page.height)
            var retainWasPartial = false
            var renderWasPartial = false
            try {
                page.retainRaster { retainWasPartial = true }
                page.render(bitmap, renderMode = TiffRenderMode.FOR_PRINT) { renderWasPartial = true }
            } finally {
                page.close()
            }

            assertTrue(retainWasPartial)
            assertTrue(renderWasPartial)
            assertEquals(argb(255, 255, 0, 0), bitmap.pixelAt(0, 0))
            assertEquals(argb(255, 0, 255, 0), bitmap.pixelAt(0, 8))
            assertEquals(argb(255, 0, 0, 255), bitmap.pixelAt(0, 16))
            assertEquals(argb(255, 0, 255, 255), bitmap.pixelAt(0, 32))
            assertEquals(argb(255, 255, 0, 255), bitmap.pixelAt(0, 40))
        }
    }

    @Test
    fun render_cleanFile_neverCallsOnPartialDecode() = runTest {
        open("single_page_rgb.tif").use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = createTiffBitmap(page.width, page.height)
            var partialDecodeCallbacks = 0
            try {
                page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY) { partialDecodeCallbacks++ }
            } finally {
                page.close()
            }
            assertEquals(0, partialDecodeCallbacks)
        }
    }
}
