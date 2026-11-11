package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest

class TiffRendererLifecycleTest {
    @Test fun concurrentPagesAndMetadataNeedNoExternalLock() = runTest {
        TiffRenderer.withDocument(Fixtures.open("varying_page_dimensions.tif")) { document ->
            val results = (0..2).map { index -> async { document.renderPage(index, RenderSize.fit(128, 128)) } }.awaitAll()
            assertEquals(3, document.pageCount())
            assertTrue(results.all { it.image.width <= 128 && it.image.height <= 128 })
        }
    }
    @Test fun closeIsIdempotentAndReturnedImagesRemainUsable() = runTest {
        val renderer = TiffRenderer.open(Fixtures.open("single_page_rgb.tif"))
        val image = renderer.renderPage(0, RenderSize.Original).image
        renderer.close(); renderer.close()
        assertEquals(0xFF8040C8.toInt(), image.pixelAt(0, 0))
        assertFailsWith<IllegalStateException> { renderer.pageCount() }
        assertFailsWith<IllegalStateException> { renderer.renderPage(0, RenderSize.Original) }
    }
    @Test fun fitPreservesAspectRatioAndDoesNotUpscaleByDefault() = runTest {
        TiffRenderer.withDocument(Fixtures.open("single_page_rgb.tif")) { r ->
            val small = r.renderPage(0, RenderSize.fit(16, 16)).image
            assertEquals(16, small.width); assertEquals(12, small.height)
            assertEquals(32, r.renderPage(0, RenderSize.fit(1000, 1000)).image.width)
            assertEquals(64, r.renderPage(0, RenderSize.fit(64, 64, allowUpscale = true)).image.width)
        }
    }
    @Test fun invalidIndicesAndIndependentSourceReuse() = runTest {
        val source = Fixtures.open("single_page_rgb.tif")
        repeat(2) { TiffRenderer.withDocument(source) { r ->
            assertFailsWith<IllegalArgumentException> { r.pageInfo(-1) }
            assertFailsWith<IllegalArgumentException> { r.renderPage(1, RenderSize.Original) }
            assertEquals(1, r.pageCount())
        } }
    }
    @Test fun inputOutputAndDecodeBudgetsFailBeforeLargeAllocation() = runTest {
        val source = Fixtures.open("single_page_rgb.tif")
        assertFailsWith<TiffIOException> { TiffRenderer.open(source, TiffOptions(limits = TiffLimits(maxInputBytes = 1))) }
        TiffRenderer.withDocument(source, TiffOptions(limits = TiffLimits(maxOutputBytes = 4))) { r ->
            assertEquals(TiffError.RESOURCE_LIMIT, assertFailsWith<TiffIOException> { r.renderPage(0, RenderSize.Original) }.reason)
        }
        TiffRenderer.withDocument(source, TiffOptions(limits = TiffLimits(maxDecodedBytes = 4))) { r ->
            assertEquals(TiffError.RESOURCE_LIMIT, assertFailsWith<TiffIOException> { r.renderPage(0, RenderSize.fit(1, 1)) }.reason)
        }
    }
    @Test fun strictPartialDecodeIsAnErrorAndTolerantDecodeReturnsWarnings() = runTest {
        val source = Fixtures.open("partially_corrupt.tif")
        TiffRenderer.withDocument(source) { r ->
            assertEquals(TiffError.PARTIAL_DECODE, assertFailsWith<TiffIOException> { r.renderPage(0, RenderSize.Original) }.reason)
        }
        TiffRenderer.withDocument(source, TiffOptions(decodePolicy = TiffDecodePolicy.ALLOW_PARTIAL)) { r ->
            assertTrue(TiffWarning.PARTIAL_DECODE in r.renderPage(0, RenderSize.Original).warnings)
        }
    }
}
