package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class TiffRendererRenderTest {

    companion object {
        private const val PAGE_WIDTH = 32
        private const val PAGE_HEIGHT = 24
        private val PAGE_COLOR = argb(255, 128, 64, 200)
    }

    private suspend fun openSinglePage() = TiffRenderer.open(Fixtures.open("single_page_rgb.tif"), Dispatchers.Unconfined)

    @Test
    fun render_destClipExceedsBitmapBounds_throwsIllegalArgumentException() = runTest {
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
    fun render_afterPageClosed_throwsIllegalStateException() = runTest {
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
    fun render_defaultTransform_fillsBitmapWithExactPageColor() = runTest {
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
    fun render_nearestVsBilinearRenderMode_bothExactForFlatColor() = runTest {
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
    fun render_withDestClip_leavesOutsideClipUntouched() = runTest {
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
    fun render_nonInvertibleTransform_throwsIllegalArgumentException() = runTest {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = createTiffBitmap(PAGE_WIDTH, PAGE_HEIGHT)
            val zeroScale = TiffTransform(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f))
            assertFailsWith<IllegalArgumentException> {
                page.render(bitmap, null, zeroScale, TiffRenderMode.FOR_DISPLAY)
            }
            page.close()
        }
    }

    @Test
    fun render_identityScaleAtSourceBoundary_samplesPureUnblendedColor() = runTest {
        // At identity scale (no mip pyramid involved), sampleBilinear's own x1/y1 clamp is what
        // keeps the last row/column's neighbor lookup in bounds; checkerboard_fine.tif has real
        // content variation at (249,249) (unlike the flat-color fixtures elsewhere in this file),
        // so a broken clamp would show up as a blended/garbage color instead of the pure cell color.
        TiffRenderer.open(Fixtures.open("checkerboard_fine.tif"), Dispatchers.Unconfined).use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = createTiffBitmap(250, 250)
            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)

            assertEquals(argb(255, 0, 0, 0), bitmap.pixelAt(249, 249))
            page.close()
        }
    }

    @Test
    fun render_withCustomTranslateTransform_offsetsContentAsSpecified() = runTest {
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
