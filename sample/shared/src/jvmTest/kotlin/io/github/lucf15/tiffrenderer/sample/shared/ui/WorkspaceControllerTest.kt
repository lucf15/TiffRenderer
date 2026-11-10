package io.github.lucf15.tiffrenderer.sample.shared.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import io.github.lucf15.tiffrenderer.TiffSource
import io.github.lucf15.tiffrenderer.sample.shared.platform.PickedTiff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class WorkspaceControllerTest {
    @Test
    fun reopeningAndDisposalDiscardPreviousWork() = runBlocking {
        val controller = WorkspaceController(this)
        val lifetime = launch { controller.ownLifetime() }
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/single_page_rgb.tif")).use {
                it.readBytes()
            }
        val input = PickedTiff("fixture.tif", TiffSource.fromByteArray(bytes))
        try {
            controller.open(input)
            controller.cancel()
            controller.open(PickedTiff("latest.tif", input.source))
            withTimeout(30_000) {
                while (controller.state.image == null) {
                    check(controller.state.stage != WorkspaceStage.FAILED) {
                        controller.state.error.orEmpty()
                    }
                    delay(5)
                }
            }
            assertEquals("latest.tif", controller.state.name)
            assertEquals(WorkspaceStage.READY, controller.state.stage)
            assertNotNull(controller.state.info)
            controller.setZoom(150)
            controller.cancel()
            withTimeout(30_000) { while (controller.state.stage != WorkspaceStage.EMPTY) delay(5) }
            assertNull(controller.state.image)
            lifetime.cancelAndJoin()
            controller.open(input)
            assertEquals(WorkspaceStage.EMPTY, controller.state.stage)
        } finally {
            lifetime.cancelAndJoin()
        }
    }

    @Test
    fun previewWorkCannotRepublishAfterDocumentClosure() = runBlocking {
        val controller = WorkspaceController(this)
        val lifetime = launch { controller.ownLifetime() }
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/single_page_rgb.tif")).use {
                it.readBytes()
            }
        try {
            controller.open(PickedTiff("preview.tif", TiffSource.fromByteArray(bytes)))
            withTimeout(30_000) { while (controller.state.image == null) delay(5) }
            controller.visiblePages(listOf(0))
            withTimeout(30_000) { while (controller.state.previews[0]?.image == null) delay(5) }
            assertNotNull(controller.state.previews[0]?.image)
            controller.cancel()
            withTimeout(30_000) { while (controller.state.stage != WorkspaceStage.EMPTY) delay(5) }
            delay(200)
            assertEquals(emptyMap(), controller.state.previews)
            assertNull(controller.state.image)
        } finally {
            lifetime.cancelAndJoin()
        }
    }

    @Test
    fun fitWidthKeepsLargeViewportOutputBounded() = runBlocking {
        val controller = WorkspaceController(this)
        val lifetime = launch { controller.ownLifetime() }
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/large_page.tiff")).use { it.readBytes() }
        try {
            controller.viewport(20_000, Int.MAX_VALUE)
            controller.open(PickedTiff("large.tiff", TiffSource.fromByteArray(bytes)))
            withTimeout(30_000) {
                while (controller.state.image == null) {
                    check(controller.state.stage != WorkspaceStage.FAILED) {
                        controller.state.error.orEmpty()
                    }
                    delay(5)
                }
            }
            val image = checkNotNull(controller.state.image)
            assertTrue(image.width.toLong() * image.height <= 8_010_000)
            assertEquals(WorkspaceStage.READY, controller.state.stage)
        } finally {
            lifetime.cancelAndJoin()
        }
    }

    @Test
    fun resizeAndZoomKeepTheVisibleFrameUntilReplacement() = runBlocking {
        val controller = WorkspaceController(this)
        val lifetime = launch { controller.ownLifetime() }
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/large_page.tiff")).use { it.readBytes() }
        try {
            controller.open(PickedTiff("large.tiff", TiffSource.fromByteArray(bytes)))
            withTimeout(30_000) { while (controller.state.image == null) delay(5) }
            val frame = checkNotNull(controller.state.image)
            controller.viewport(640, 480)
            assertSame(frame, controller.state.image)
            controller.setZoom(150)
            assertSame(frame, controller.state.image)
            controller.viewport(800, 600)
            assertSame(frame, controller.state.image)
            withTimeout(30_000) { while (controller.state.rendering) delay(5) }
            assertEquals(1200, controller.state.image?.width)
            assertEquals(900, controller.state.image?.height)
        } finally {
            lifetime.cancelAndJoin()
        }
    }

    @Test
    fun cachedPageSelectionShowsTheCorrectPreviewWithoutAnEmptyFrame() = runBlocking {
        val controller = WorkspaceController(this)
        val lifetime = launch { controller.ownLifetime() }
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/varying_page_dimensions.tif")).use {
                it.readBytes()
            }
        try {
            controller.open(PickedTiff("mixed.tiff", TiffSource.fromByteArray(bytes)))
            withTimeout(30_000) { while (controller.state.image == null) delay(5) }
            assertTrue(controller.state.pageCount > 1)
            controller.visiblePages(listOf(0, 1), 320, 480)
            withTimeout(30_000) { while (controller.state.previews[1]?.image == null) delay(5) }
            val preview = checkNotNull(controller.state.previews[1]?.image)
            controller.select(1)
            assertSame(preview, controller.state.image)
            assertNotNull(controller.state.info)
            assertEquals(1, controller.state.pageIndex)
            withTimeout(30_000) { while (controller.state.rendering) delay(5) }
            assertNotNull(controller.state.image)
            assertEquals(1, controller.state.pageIndex)
        } finally {
            lifetime.cancelAndJoin()
        }
    }

    @Test
    fun recentPageFramesAreReusedAndNeverCrossDocumentBoundaries() = runBlocking {
        val controller = WorkspaceController(this)
        val lifetime = launch { controller.ownLifetime() }
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/varying_page_dimensions.tif")).use {
                it.readBytes()
            }
        try {
            controller.open(PickedTiff("mixed.tif", TiffSource.fromByteArray(bytes)))
            withTimeout(30_000) { while (controller.state.image == null) delay(5) }
            val original = checkNotNull(controller.state.image)
            controller.select(1)
            withTimeout(30_000) { while (controller.state.rendering) delay(5) }
            controller.select(0)
            assertSame(original, controller.state.image)
            assertEquals(false, controller.state.rendering)
            controller.viewport(100, 100)
            assertSame(original, controller.state.image)
            assertEquals(false, controller.state.rendering)
            val replacement =
                checkNotNull(javaClass.getResourceAsStream("/large_page.tiff")).use {
                    it.readBytes()
                }
            controller.open(PickedTiff("replacement.tiff", TiffSource.fromByteArray(replacement)))
            withTimeout(30_000) {
                while (
                    controller.state.name != "replacement.tiff" || controller.state.image == null
                ) delay(5)
            }
            assertEquals(4000, controller.state.info?.width)
            assertEquals(3000, controller.state.info?.height)
        } finally {
            lifetime.cancelAndJoin()
        }
    }

    @Test
    fun zoomDoesNotInvalidateReadersOfThePageIndex() = runBlocking {
        val controller = WorkspaceController(this)
        var invalidations = 0
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            observer.observeReads(Any(), { _: Any -> invalidations++ }) {
                controller.state.pageIndex
            }
            controller.setZoom(150)
            Snapshot.sendApplyNotifications()
            assertEquals(150, controller.state.zoom)
            assertEquals(0, invalidations)
        } finally {
            observer.stop()
            observer.clear()
        }
    }
}
