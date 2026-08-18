package io.github.lucf15.tiffrenderer

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Hostile/corrupt TIFFs (adversarial dimensions, truncated data) must surface as
 * [TiffIOException], never a crash. */
class TiffRendererCorruptInputTest {

    private fun open(name: String) =
        TiffRenderer(TiffSource.fromParcelFileDescriptor(TestFixtures.open(name)))

    @Test
    fun constructor_garbageBytes_throwsTiffIOException() {
        assertThrows(TiffIOException::class.java) { open("not_a_tiff.bin") }
    }

    /** huge_dimensions.tif claims a valid-but-100000x100000 page; render() must reject the
     * ~40GB allocation, not crash. */
    @Test
    fun render_hugeDimensionsPage_throwsTiffIOExceptionInsteadOfCrashing() {
        open("huge_dimensions.tif").use { renderer ->
            val page = renderer.openPage(0)
            assertEquals(100000, page.width)
            assertEquals(100000, page.height)

            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
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

    /** Same allocation-failure path, reached through retainRaster() instead of render(). */
    @Test
    fun retainRaster_hugeDimensionsPage_throwsTiffIOExceptionInsteadOfCrashing() {
        open("huge_dimensions.tif").use { renderer ->
            val page = renderer.openPage(0)
            try {
                assertThrows(TiffIOException::class.java) { page.retainRaster() }
            } finally {
                page.close()
            }
        }
    }

    /** truncated.tif is missing the last 40 bytes of its strip data: a real short read, not a
     * dimensions problem. */
    @Test
    fun render_truncatedFile_throwsTiffIOException() {
        open("truncated.tif").use { renderer ->
            val page = renderer.openPage(0) // succeeds: the IFD itself is intact
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
}
