package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class TiffV3CancellationTest {
    @Test fun cancellationDuringNativeHandleHandoffFreesThePointer() = runTest {
        var frees = 0
        lateinit var job: Job
        val dispatcher = StandardTestDispatcher(testScheduler)
        job = launch {
            acquireTiffHandle(dispatcher, { job.cancel(); 42L }, { pointer ->
                assertEquals(42L, pointer)
                frees++
            })
        }
        job.join()
        assertEquals(1, frees)
    }

    @Test fun closeCompletesEvenWhenTheCallingJobIsCancelled() = runTest {
        val document = TiffRenderer.open(Fixtures.open("single_page_rgb.tif"))
        val job = launch { cancel(); document.close() }
        job.join()
        document.close()
        assertFailsWith<IllegalStateException> { document.pageCount() }
    }
}
