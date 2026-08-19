package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertTrue

class TiffRendererMinificationTest {

    @Test
    fun render_downscaledFineCheckerboard_blendsInsteadOfAliasing() {
        TiffRenderer(Fixtures.open("checkerboard_fine.tif")).use { renderer ->
            val page = renderer.openPage(0)

            val bitmap = createTiffBitmap(29, 29)
            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)

            for (y in 0 until 29) {
                for (x in 0 until 29) {
                    val pixel = bitmap.pixelAt(x, y)
                    val red = (pixel ushr 16) and 0xFF
                    assertTrue(
                        red in 60..195,
                        "pixel ($x,$y) red=$red should be a blended gray, not aliased black/white",
                    )
                }
            }
            page.close()
        }
    }
}
