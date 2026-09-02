package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/** See jvmTest's copy of this test for why it's duplicated rather than shared via integrationTest. */
class TiffBitmapToIntArrayTest {
    @Test
    fun toIntArray_matchesPixelAtConvention() = runTest {
        TiffRenderer.open(Fixtures.open("single_page_rgb.tif"), Dispatchers.Unconfined).use { renderer ->
            renderer.openPage(0).use { page ->
                val bitmap = createTiffBitmap(page.width, page.height)
                page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)

                val expected = argb(255, 128, 64, 200)
                assertEquals(expected, bitmap.pixelAt(0, 0))
                assertEquals(expected, bitmap.toIntArray()[0])
            }
        }
    }
}
