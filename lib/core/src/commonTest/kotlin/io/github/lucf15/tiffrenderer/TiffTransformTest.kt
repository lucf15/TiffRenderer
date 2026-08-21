package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class TiffTransformTest {
    @Test
    fun rejectsWrongSizedArray() {
        assertFailsWith<IllegalArgumentException> { TiffTransform(floatArrayOf(1f, 2f, 3f)) }
    }

    @Test
    fun acceptsSixElements() {
        TiffTransform(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f))
    }

    @Test
    fun valuesIsDefensivelyCopiedBothWays() {
        val source = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
        val transform = TiffTransform(source)
        source[0] = 999f
        transform.values[0] = 999f
        assertContentEquals(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f), transform.values)
    }
}

class TiffRectTest {
    @Test
    fun rejectsDegenerateRect() {
        assertFailsWith<IllegalArgumentException> { TiffRect(10, 10, 10, 20) }
        assertFailsWith<IllegalArgumentException> { TiffRect(10, 10, 20, 10) }
    }

    @Test
    fun computesWidthAndHeight() {
        val rect = TiffRect(5, 5, 15, 20)
        require(rect.width == 10)
        require(rect.height == 15)
    }
}
