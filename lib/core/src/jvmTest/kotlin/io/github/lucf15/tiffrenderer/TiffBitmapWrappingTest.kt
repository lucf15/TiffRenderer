package io.github.lucf15.tiffrenderer

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TiffBitmapWrappingTest {

    @Test
    fun wrapping_reusedAcrossRepeatedRenders_producesCorrectPixelsEachTime() {
        TiffRenderer(Fixtures.open("single_page_rgb.tif")).use { renderer ->
            val buffer = ByteBuffer.allocateDirect(32 * 24 * 4)
            repeat(5) {
                renderer.openPage(0).use { page ->
                    val bitmap = TiffBitmap.wrapping(buffer, 32, 24)
                    page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                    assertEquals(argb(255, 128, 64, 200), bitmap.pixelAt(0, 0))
                }
            }
        }
    }

    @Test
    fun wrapping_bufferTooSmall_throwsIllegalArgumentException() {
        val buffer = ByteBuffer.allocateDirect(10 * 10 * 4)
        assertFailsWith<IllegalArgumentException> { TiffBitmap.wrapping(buffer, 20, 20) }
    }

    @Test
    fun wrapping_nonDirectBuffer_throwsIllegalArgumentException() {
        val buffer = ByteBuffer.allocate(10 * 10 * 4)
        assertFailsWith<IllegalArgumentException> { TiffBitmap.wrapping(buffer, 10, 10) }
    }

    @Test
    fun wrapping_largerArenaBuffer_toByteArrayReturnsExactImageSizeNotArenaSize() {
        TiffRenderer(Fixtures.open("single_page_rgb.tif")).use { renderer ->
            val imageBytes = 32 * 24 * 4
            val arena = ByteBuffer.allocateDirect(imageBytes * 3)
            renderer.openPage(0).use { page ->
                val bitmap = TiffBitmap.wrapping(arena, 32, 24)
                page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                assertEquals(imageBytes, bitmap.toByteArray().size)
            }
        }
    }

    @Test
    fun wrapping_positionedArenaBuffer_rendersAtThatOffsetNotArenaStart() {
        TiffRenderer(Fixtures.open("single_page_rgb.tif")).use { renderer ->
            val imageBytes = 32 * 24 * 4
            val arena = ByteBuffer.allocateDirect(imageBytes * 2)
            val sentinel: Byte = 0x11
            for (i in 0 until arena.capacity()) arena.put(i, sentinel)
            arena.position(imageBytes)

            renderer.openPage(0).use { page ->
                val bitmap = TiffBitmap.wrapping(arena, 32, 24)
                page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                assertEquals(argb(255, 128, 64, 200), bitmap.pixelAt(0, 0))
            }

            // The first image-sized region, before the offset TiffBitmap.wrapping was given,
            // must still hold the sentinel: a render through a positioned buffer must land at
            // that offset, not silently at the arena's base address.
            for (i in 0 until imageBytes) {
                assertEquals(sentinel, arena.get(i), "byte $i before the offset was overwritten")
            }
        }
    }

    @Test
    fun wrapping_bufferWithLimitLessThanCapacity_confinesRenderToLimitNotCapacity() {
        TiffRenderer(Fixtures.open("single_page_rgb.tif")).use { renderer ->
            val imageBytes = 32 * 24 * 4
            val arena = ByteBuffer.allocateDirect(imageBytes * 2)
            val sentinel: Byte = 0x22
            for (i in 0 until arena.capacity()) arena.put(i, sentinel)
            arena.position(0)
            arena.limit(imageBytes)

            renderer.openPage(0).use { page ->
                val bitmap = TiffBitmap.wrapping(arena, 32, 24)
                page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                assertEquals(argb(255, 128, 64, 200), bitmap.pixelAt(0, 0))
                assertEquals(imageBytes, bitmap.toByteArray().size)
            }

            // arena.get(int) checks the buffer's own limit, so read through a duplicate with it restored.
            val fullView = arena.duplicate().apply { limit(capacity()) }
            for (i in imageBytes until arena.capacity()) {
                assertEquals(sentinel, fullView.get(i), "byte $i beyond the limit was overwritten")
            }
        }
    }
}
