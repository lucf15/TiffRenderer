package io.github.lucf15.tiffrenderer

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/** Regression coverage for a [TiffCoreHandle] use-after-free: a thread blocked on the render lock
 * used to resume with a freed pointer once [close] released it. It now zeroes the pointer under
 * that same lock (a [kotlinx.coroutines.sync.Mutex], safe across real OS threads via
 * [runBlocking] just like across coroutines), so a racing call sees a closed handle instead. */
class TiffCoreHandleCloseRaceTest {

    companion object {
        private const val PAGE_WIDTH = 32
        private const val PAGE_HEIGHT = 24
    }

    @Test
    fun renderRacingClose_neverTouchesAFreedPointer() = runTest {
        repeat(50) {
            val renderer = TiffRenderer.open(Fixtures.open("single_page_rgb.tif"))
            val page = renderer.openPage(0)
            val ready = CountDownLatch(1)
            val otherFailures = ConcurrentLinkedQueue<Throwable>()

            val renderThread = Thread {
                ready.await()
                repeat(200) {
                    try {
                        runBlocking {
                            val bitmap = createTiffBitmap(PAGE_WIDTH, PAGE_HEIGHT)
                            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
                        }
                    } catch (e: IllegalStateException) {
                        // Expected once close() wins the race.
                    } catch (t: Throwable) {
                        otherFailures += t
                    }
                }
            }
            val closeThread = Thread {
                ready.await()
                runBlocking {
                    page.close()
                    renderer.close()
                }
            }

            renderThread.start()
            closeThread.start()
            ready.countDown()
            renderThread.join()
            closeThread.join()

            assertTrue(otherFailures.isEmpty(), otherFailures.joinToString { it.toString() })
        }
    }

    @Test
    fun closeOnce_calledTwiceOnTheSameHandle_doesNotDoubleFree() = runTest {
        // Exercises TiffCoreHandle.closeOnce directly (bypassing TiffRenderer's own closed-state
        // guard, which intentionally throws on a public double-close) to prove the native-level
        // idempotency the lock-guarded pointer zeroing is meant to provide.
        val source = Fixtures.open("single_page_rgb.tif")
        val handle = TiffCoreBinding.open(source, defaultTiffDispatcher)
        TiffCoreBinding.close(handle, defaultTiffDispatcher)
        TiffCoreBinding.close(handle, defaultTiffDispatcher)
        source.release()
    }
}
