package io.github.lucf15.tiffrenderer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The Android-native convenience overloads (`android.graphics.Bitmap`/`Matrix`/`Rect` directly,
 * no common [TiffBitmap]/[TiffTransform]/[TiffRect] wrapping) that only exist on this platform;
 * everything shared with JVM/iOS lives in `integrationTest` instead. */
class TiffRendererAndroidNativeOverloadTest {

    companion object {
        private const val PAGE_WIDTH = 32
        private const val PAGE_HEIGHT = 24
        private val PAGE_COLOR = Color.rgb(128, 64, 200)
    }

    private fun openSinglePage() =
        TiffRenderer(TiffSource.fromParcelFileDescriptor(openFixturePfd("single_page_rgb.tif")))

    @Test
    fun tiffBitmap_wrongBitmapConfig_throwsIllegalArgumentException() {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.RGB_565)
        assertThrows(IllegalArgumentException::class.java) { TiffBitmap(bitmap) }
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
        TiffRenderer(openFixturePfd("single_page_rgb.tif")).use { renderer ->
            assertEquals(1, renderer.pageCount)
        }
    }
}
