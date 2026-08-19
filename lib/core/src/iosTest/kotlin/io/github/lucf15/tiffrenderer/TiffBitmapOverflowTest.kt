package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertFailsWith

class TiffBitmapOverflowTest {
    @Test
    fun createTiffBitmap_widthTimesHeightOverflowsInt_throwsIllegalArgumentException() {
        assertFailsWith<IllegalArgumentException> { createTiffBitmap(50_000, 50_000) }
    }
}
