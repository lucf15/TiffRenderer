package io.github.lucf15.tiffrenderer

import com.goncalossilva.resources.Resource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TiffSourceByteArrayTest {
    @Test
    fun fromByteArray_rendersRealFixtureWithoutTouchingDisk() {
        val bytes = Resource("single_page_rgb.tif").readBytes()

        TiffRenderer(TiffSource.fromByteArray(bytes)).use { renderer ->
            assertEquals(1, renderer.pageCount)
            val page = renderer.openPage(0)

            val bitmap = createTiffBitmap(page.width, page.height)
            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
            assertEquals(argb(255, 128, 64, 200), bitmap.pixelAt(0, 0))

            page.close()
        }
    }

    @Test
    fun fromByteArray_garbageBytes_throwsTiffIOException() {
        val bytes = Resource("not_a_tiff.bin").readBytes()
        assertFailsWith<TiffIOException> { TiffRenderer(TiffSource.fromByteArray(bytes)) }
    }

    @Test
    fun fromByteArray_clearsRetainedReferenceAfterOpen() {
        val bytes = Resource("single_page_rgb.tif").readBytes()
        val source = TiffSource.fromByteArray(bytes)
        TiffRenderer(source).use { }
        assertNull(source.bytes)
    }
}
