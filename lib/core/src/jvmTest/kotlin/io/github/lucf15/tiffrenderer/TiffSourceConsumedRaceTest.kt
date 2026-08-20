package io.github.lucf15.tiffrenderer

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regression coverage for [TiffSource.markConsumed]: the old non-atomic check-then-set let two
 * threads racing to construct a [TiffRenderer] over the same source both proceed. It's a real
 * compare-and-set now, so exactly one caller can win. */
class TiffSourceConsumedRaceTest {

    @Test
    fun construction_racingOverOneSource_exactlyOneSucceeds() {
        repeat(20) {
            val source = Fixtures.open("single_page_rgb.tif")
            val ready = CountDownLatch(1)
            val rejections = AtomicInteger(0)
            val winners = ConcurrentLinkedQueue<TiffRenderer>()

            val threads = List(4) {
                Thread {
                    ready.await()
                    repeat(50) {
                        try {
                            winners += TiffRenderer(source)
                        } catch (_: IllegalStateException) {
                            rejections.incrementAndGet()
                        }
                    }
                }
            }
            threads.forEach { it.start() }
            ready.countDown()
            threads.forEach { it.join() }

            assertEquals(1, winners.size, "expected exactly one TiffRenderer construction to win the race")
            assertEquals(4 * 50 - 1, rejections.get())
            winners.first().close()
        }
    }

    /** Regression coverage for [TiffSource.release]: it used to run unconditionally every time
     * [TiffRenderer.close] reached it, so two threads racing a `close()` call that both passed the
     * (non-atomic) `!closed` check would both release the same underlying resource. It's a
     * compare-and-set now too, so only the first caller actually releases anything. */
    @Test
    fun close_racingFromMultipleThreads_neverThrowsUnexpectedly() {
        repeat(20) {
            val renderer = TiffRenderer(Fixtures.open("single_page_rgb.tif"))
            val ready = CountDownLatch(1)
            val unexpected = ConcurrentLinkedQueue<Throwable>()

            val threads = List(4) {
                Thread {
                    ready.await()
                    try {
                        renderer.close()
                    } catch (_: IllegalStateException) {
                        // Expected for every loser of the race.
                    } catch (t: Throwable) {
                        unexpected += t
                    }
                }
            }
            threads.forEach { it.start() }
            ready.countDown()
            threads.forEach { it.join() }

            assertTrue(unexpected.isEmpty(), unexpected.joinToString { it.toString() })
        }
    }
}
