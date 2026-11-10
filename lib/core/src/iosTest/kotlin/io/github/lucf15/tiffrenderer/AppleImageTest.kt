@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.lucf15.tiffrenderer

import kotlinx.cinterop.useContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL

class AppleImageTest {
    @Test fun nativeImagesOutliveTheirDocuments() = runTest {
        val image = TiffRenderer.withDocument(Fixtures.open("single_page_rgb.tif")) {
            it.renderPage(0, RenderSize.fit(16, 16)).image.toUIImage()
        }
        assertNotNull(image.CGImage)
        assertEquals(16.0, image.size.useContents { width })
        assertEquals(12.0, image.size.useContents { height })
        val oneShot = AppleTiffRenderer.render(NSURL.fileURLWithPath(NSTemporaryDirectory() + "single_page_rgb.tif"), 0, 16, 16)
        assertNotNull(oneShot.CGImage)
    }
}
