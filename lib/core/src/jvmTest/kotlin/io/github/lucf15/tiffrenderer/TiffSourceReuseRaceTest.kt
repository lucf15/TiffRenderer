package io.github.lucf15.tiffrenderer

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class TiffSourceReuseRaceTest {

    @Test
    fun concurrentOpensOverOneSourceAllAcquireIndependentDocuments() = runTest {
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
                            runBlocking { winners += TiffRenderer.open(source) }
                        } catch (_: IllegalStateException) {
                            rejections.incrementAndGet()
                        }
                    }
                }
            }
            threads.forEach { it.start() }
            ready.countDown()
            threads.forEach { it.join() }

            assertEquals(4 * 50, winners.size, "every open must acquire an independent document")
            assertEquals(0, rejections.get())
            winners.forEach { it.close() }
        }
    }

    @Test
    fun close_racingFromMultipleThreads_neverThrowsUnexpectedly() = runTest {
        repeat(20) {
            val renderer = TiffRenderer.open(Fixtures.open("single_page_rgb.tif"))
            val ready = CountDownLatch(1)
            val unexpected = ConcurrentLinkedQueue<Throwable>()

            val threads = List(4) {
                Thread {
                    ready.await()
                    try {
                        runBlocking { renderer.close() }
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
