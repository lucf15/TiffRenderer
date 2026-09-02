package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** Not in the shared integrationTest source set: androidDeviceTest also compiles against it, and
 * Android's TiffBitmap has no toIntArray(). Duplicated instead into jvmTest and iosTest, the only
 * two platforms with a toIntArray(). Regression coverage for a real bug: toIntArray() once packed
 * R and B into swapped byte positions relative to pixelAt()'s ARGB convention. single_page_rgb.tif's
 * fixed color (r=128, b=200) has r != b, so a channel swap wouldn't go unnoticed here the way it
 * would on a gray or r==b fixture. */
class TiffBitmapToIntArrayTest {
    @Test
    fun toIntArray_matchesPixelAtConvention() = runTest {
        TiffRenderer.open(Fixtures.open("single_page_rgb.tif")).use { renderer ->
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
