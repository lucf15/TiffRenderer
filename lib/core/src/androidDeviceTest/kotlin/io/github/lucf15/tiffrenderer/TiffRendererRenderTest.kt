package io.github.lucf15.tiffrenderer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [TiffPage.render]'s argument validation and pixel-level correctness, using a flat-color fixture
 * so pixel assertions stay exact. Three cases have no equivalent here: the common Kotlin types
 * [TiffPage.render] takes are structurally stronger than a raw Bitmap/Rect/Matrix/int quartet, so
 * the invalid states those cases would need can't be constructed at all:
 *  - null destination: `destination` is a non-null [TiffBitmap], so passing `null` is a compile
 *    error, not a runtime check.
 *  - non-affine transform: [TiffTransform] only ever stores the 6 affine components; there's no
 *    way to construct a non-affine one to pass.
 *  - invalid render mode: [TiffRenderMode] is a Kotlin enum; there's no "invalid" ordinal.
 * Two more cases (wrong bitmap config, degenerate clip) survive but moved to where the validation
 * now actually happens: [TiffBitmap]'s and [TiffRect]'s own constructors (see their `init`
 * blocks), since both fail fast well before a `render()` call.
 */
class TiffRendererRenderTest {

    companion object {
        private const val PAGE_WIDTH = 32
        private const val PAGE_HEIGHT = 24
        private val PAGE_COLOR = Color.rgb(128, 64, 200)
    }

    private fun openSinglePage() =
        TiffRenderer(TiffSource.fromParcelFileDescriptor(TestFixtures.open("single_page_rgb.tif")))

    @Test
    fun tiffBitmap_wrongBitmapConfig_throwsIllegalArgumentException() {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.RGB_565)
        assertThrows(IllegalArgumentException::class.java) { TiffBitmap(bitmap) }
    }

    @Test
    fun render_destClipExceedsBitmapBounds_throwsIllegalArgumentException() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = TiffBitmap(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
            val tooWide = TiffRect(0, 0, 20, 5)
            assertThrows(IllegalArgumentException::class.java) {
                page.render(bitmap, tooWide, null, TiffRenderMode.FOR_DISPLAY)
            }
            page.close()
        }
    }

    @Test
    fun tiffRect_degenerate_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) { TiffRect(5, 5, 5, 8) } // left == right
    }

    @Test
    fun render_afterPageClosed_throwsIllegalStateException() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            page.close()
            val bitmap = TiffBitmap(Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888))
            assertThrows(IllegalStateException::class.java) {
                page.render(bitmap, null, null, TiffRenderMode.FOR_DISPLAY)
            }
        }
    }

    @Test
    fun render_defaultTransform_fillsBitmapWithExactPageColor() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            page.render(TiffBitmap(bitmap), renderMode = TiffRenderMode.FOR_DISPLAY)

            assertEquals(PAGE_COLOR, bitmap.getPixel(0, 0))
            assertEquals(PAGE_COLOR, bitmap.getPixel(PAGE_WIDTH - 1, PAGE_HEIGHT - 1))
            assertEquals(255, Color.alpha(bitmap.getPixel(PAGE_WIDTH / 2, PAGE_HEIGHT / 2)))
            page.close()
        }
    }

    @Test
    fun render_nearestVsBilinearRenderMode_bothExactForFlatColor() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            page.render(TiffBitmap(bitmap), renderMode = TiffRenderMode.FOR_PRINT)

            assertEquals(PAGE_COLOR, bitmap.getPixel(0, 0))
            assertEquals(PAGE_COLOR, bitmap.getPixel(PAGE_WIDTH - 1, PAGE_HEIGHT - 1))
            page.close()
        }
    }

    @Test
    fun render_withDestClip_leavesOutsideClipUntouched() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
            val sentinel = Color.YELLOW
            bitmap.eraseColor(sentinel)

            val clip = TiffRect(10, 10, 20, 18)
            page.render(TiffBitmap(bitmap), clip, null, TiffRenderMode.FOR_DISPLAY)

            assertEquals(PAGE_COLOR, bitmap.getPixel(15, 14))
            assertEquals(sentinel, bitmap.getPixel(5, 5))
            assertEquals(sentinel, bitmap.getPixel(9, 14))
            assertEquals(sentinel, bitmap.getPixel(20, 14))
            assertEquals(sentinel, bitmap.getPixel(15, 9))
            assertEquals(sentinel, bitmap.getPixel(15, 18))
            page.close()
        }
    }

    @Test
    fun render_withCustomTranslateTransform_offsetsContentAsSpecified() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(50, 40, Bitmap.Config.ARGB_8888)
            val sentinel = Color.BLUE
            bitmap.eraseColor(sentinel)

            // TiffTransform is [mxx, mxy, mtx, myx, myy, mty]: identity scale, translate by (5,5).
            val translated = TiffTransform(floatArrayOf(1f, 0f, 5f, 0f, 1f, 5f))
            page.render(TiffBitmap(bitmap), null, translated, TiffRenderMode.FOR_DISPLAY)

            assertEquals(sentinel, bitmap.getPixel(0, 0))
            assertEquals(PAGE_COLOR, bitmap.getPixel(5, 5))
            assertEquals(PAGE_COLOR, bitmap.getPixel(5 + PAGE_WIDTH - 1, 5 + PAGE_HEIGHT - 1))
            page.close()
        }
    }

    @Test
    fun render_androidNativeOverload_matchesCommonTypesResult() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(50, 40, Bitmap.Config.ARGB_8888)
            val sentinel = Color.BLUE
            bitmap.eraseColor(sentinel)

            val matrix = Matrix().apply { setTranslate(5f, 5f) }
            page.render(bitmap, null, matrix, TiffRenderMode.FOR_DISPLAY)

            assertEquals(sentinel, bitmap.getPixel(0, 0))
            assertEquals(PAGE_COLOR, bitmap.getPixel(5, 5))
            assertEquals(PAGE_COLOR, bitmap.getPixel(5 + PAGE_WIDTH - 1, 5 + PAGE_HEIGHT - 1))
            page.close()
        }
    }

    @Test
    fun render_androidNativeOverload_destClipMatchesTiffRectResult() {
        openSinglePage().use { renderer ->
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
            val sentinel = Color.YELLOW
            bitmap.eraseColor(sentinel)

            page.render(bitmap, Rect(10, 10, 20, 18), null, TiffRenderMode.FOR_DISPLAY)

            assertEquals(PAGE_COLOR, bitmap.getPixel(15, 14))
            assertEquals(sentinel, bitmap.getPixel(5, 5))
            page.close()
        }
    }

    @Test
    fun matrixToTiffTransform_nonAffineMatrix_throwsIllegalArgumentException() {
        val matrix = Matrix().apply {
            setValues(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f))
        }
        assertThrows(IllegalArgumentException::class.java) { matrix.toTiffTransform() }
    }

    @Test
    fun tiffRenderer_androidNativeOverload_opensPfdDirectly() {
        TiffRenderer(TestFixtures.open("single_page_rgb.tif")).use { renderer ->
            assertEquals(1, renderer.pageCount)
        }
    }
}
