package io.github.lucf15.tiffrenderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TiffRendererRetainRasterTest {
    @Test fun boundedCacheSurvivesPageChangesAndCanBeTrimmed() = runTest {
        TiffRenderer.withDocument(Fixtures.open("varying_page_dimensions.tif"), TiffOptions(cacheBytes = 1024 * 1024)) { r ->
            val first = r.renderPage(0, RenderSize.Original).image
            assertTrue(first.contentEquals(r.renderPage(0, RenderSize.Original).image))
            r.renderPage(1, RenderSize.Original)
            r.trimCache()
            assertTrue(first.contentEquals(r.renderPage(0, RenderSize.Original).image))
        }
    }
    @Test fun partialCacheKeepsItsWarningOnEveryRender() = runTest {
        TiffRenderer.withDocument(Fixtures.open("partially_corrupt.tif"), TiffOptions(decodePolicy = TiffDecodePolicy.ALLOW_PARTIAL, cacheBytes = 1024 * 1024)) { r ->
            repeat(3) { assertEquals(setOf(TiffWarning.PARTIAL_DECODE), r.renderPage(0, RenderSize.Original).warnings) }
        }
    }
}
