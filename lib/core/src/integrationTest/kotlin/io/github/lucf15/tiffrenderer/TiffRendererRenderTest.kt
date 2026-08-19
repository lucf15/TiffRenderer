package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TiffRendererRenderTest {

    companion object {
        private const val PAGE_WIDTH = 32
        private const val PAGE_HEIGHT = 24
        private val PAGE_COLOR = argb(255, 128, 64, 200)
    }

    private fun openSinglePage() = TiffRenderer(Fixtures.open("single_page_rgb.tif"))

    @Test
    fun render_destClipExceedsBitmapBounds_throwsIllegalArgumentException() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = createTiffBitmap(10, 10)
            val tooWide = TiffRect(0, 0, 20, 5)
            assertFailsWith<IllegalArgumentException> {
                page.render(bitmap, tooWide, null, TiffRenderMode.FOR_DISPLAY)
            }
            page.close()
        }
    }

    @Test
    fun render_afterPageClosed_throwsIllegalStateException() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            page.close()
            val bitmap = createTiffBitmap(PAGE_WIDTH, PAGE_HEIGHT)
            assertFailsWith<IllegalStateException> {
                page.render(bitmap, null, null, TiffRenderMode.FOR_DISPLAY)
            }
        }
    }

    @Test
    fun render_defaultTransform_fillsBitmapWithExactPageColor() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = createTiffBitmap(PAGE_WIDTH, PAGE_HEIGHT)
            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)

            assertEquals(PAGE_COLOR, bitmap.pixelAt(0, 0))
            assertEquals(PAGE_COLOR, bitmap.pixelAt(PAGE_WIDTH - 1, PAGE_HEIGHT - 1))
            assertEquals(255, (bitmap.pixelAt(PAGE_WIDTH / 2, PAGE_HEIGHT / 2) ushr 24) and 0xFF)
            page.close()
        }
    }

    @Test
    fun render_nearestVsBilinearRenderMode_bothExactForFlatColor() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = createTiffBitmap(PAGE_WIDTH, PAGE_HEIGHT)
            page.render(bitmap, renderMode = TiffRenderMode.FOR_PRINT)

            assertEquals(PAGE_COLOR, bitmap.pixelAt(0, 0))
            assertEquals(PAGE_COLOR, bitmap.pixelAt(PAGE_WIDTH - 1, PAGE_HEIGHT - 1))
            page.close()
        }
    }

    @Test
    fun render_withDestClip_leavesOutsideClipUntouched() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = createTiffBitmap(64, 48)

            val clip = TiffRect(10, 10, 20, 18)
            page.render(bitmap, clip, null, TiffRenderMode.FOR_DISPLAY)

            assertEquals(PAGE_COLOR, bitmap.pixelAt(15, 14))
            assertEquals(0, bitmap.pixelAt(5, 5))
            assertEquals(0, bitmap.pixelAt(9, 14))
            assertEquals(0, bitmap.pixelAt(20, 14))
            assertEquals(0, bitmap.pixelAt(15, 9))
            assertEquals(0, bitmap.pixelAt(15, 18))
            page.close()
        }
    }

    @Test
    fun render_withCustomTranslateTransform_offsetsContentAsSpecified() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = createTiffBitmap(50, 40)

            val translated = TiffTransform(floatArrayOf(1f, 0f, 5f, 0f, 1f, 5f))
            page.render(bitmap, null, translated, TiffRenderMode.FOR_DISPLAY)

            assertEquals(0, bitmap.pixelAt(0, 0))
            assertEquals(PAGE_COLOR, bitmap.pixelAt(5, 5))
            assertEquals(PAGE_COLOR, bitmap.pixelAt(5 + PAGE_WIDTH - 1, 5 + PAGE_HEIGHT - 1))
            page.close()
        }
    }
}
