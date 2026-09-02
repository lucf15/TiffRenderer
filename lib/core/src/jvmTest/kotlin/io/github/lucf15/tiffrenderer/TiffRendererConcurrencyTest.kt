package io.github.lucf15.tiffrenderer

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/** Regression coverage for the per-[TiffCoreHandle] native lock: iOS's binding used to have no
 * lock at all, so concurrent native calls into the same document were a real memory-safety risk,
 * not just a theoretical one. These tests prove concurrent access through the same lock stays
 * correct, and that two independent documents (two separate locks) don't corrupt each other. Real
 * [Thread]s, not coroutines, so each bridges in via [runBlocking] — a [kotlinx.coroutines.sync.Mutex]
 * is safe across real OS threads the same way it is across coroutines. */
class TiffRendererConcurrencyTest {

    companion object {
        private const val PAGE_WIDTH = 32
        private const val PAGE_HEIGHT = 24
        private val PAGE_COLOR = argb(255, 128, 64, 200)
    }

    @Test
    fun render_concurrentCallsOnSamePage_allProduceCorrectPixels() = runTest {
        TiffRenderer.open(Fixtures.open("single_page_rgb.tif")).use { renderer ->
            val page = renderer.openPage(0)
            val failures = ConcurrentLinkedQueue<String>()

            val threads = List(8) { threadIndex ->
                Thread {
                    repeat(25) {
                        runBlocking {
                            val bitmap = createTiffBitmap(PAGE_WIDTH, PAGE_HEIGHT)
                            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                            if (bitmap.pixelAt(0, 0) != PAGE_COLOR ||
                                bitmap.pixelAt(PAGE_WIDTH - 1, PAGE_HEIGHT - 1) != PAGE_COLOR
                            ) {
                                failures += "thread $threadIndex saw corrupted pixel data"
                            }
                        }
                    }
                }
            }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            page.close()

            assertTrue(failures.isEmpty(), failures.joinToString())
        }
    }

    @Test
    fun render_twoIndependentRenderersOnSeparateThreads_neitherCorruptsTheOther() = runTest {
        val failures = ConcurrentLinkedQueue<String>()

        val threads = List(2) { threadIndex ->
            Thread {
                runBlocking {
                    TiffRenderer.open(Fixtures.open("single_page_rgb.tif")).use { renderer ->
                        repeat(20) {
                            val page = renderer.openPage(0)
                            val bitmap = createTiffBitmap(PAGE_WIDTH, PAGE_HEIGHT)
                            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                            if (bitmap.pixelAt(0, 0) != PAGE_COLOR) {
                                failures += "thread $threadIndex saw corrupted pixel data"
                            }
                            page.close()
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue(failures.isEmpty(), failures.joinToString())
    }
}
