package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TiffRendererCorruptInputTest {

    private fun open(name: String) = TiffRenderer(Fixtures.open(name))

    @Test
    fun constructor_garbageBytes_throwsTiffIOException() {
        assertFailsWith<TiffIOException> { open("not_a_tiff.bin") }
    }

    @Test
    fun render_hugeDimensionsPage_throwsTiffIOExceptionInsteadOfCrashing() {
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
    fun retainRaster_hugeDimensionsPage_throwsTiffIOExceptionInsteadOfCrashing() {
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
    fun render_truncatedFile_throwsTiffIOException() {
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
}
