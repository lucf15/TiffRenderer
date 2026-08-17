/*
 * Copyright 2026 lucf15
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.lucf15.tiffrenderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/** The codec support matrix from CMakeLists.txt: supported codecs must fully decode; unsupported ones must fail specifically from render(), never openPage(). */
@RunWith(AndroidJUnit4.class)
public class TiffRendererCodecTest {

    private static final int RGB_PAGE_COUNT = 3;

    private void assertDecodesAllPages(String assetName, int expectedPageCount) throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open(assetName);
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            assertEquals(expectedPageCount, renderer.getPageCount());
            for (int i = 0; i < renderer.getPageCount(); i++) {
                TiffRenderer.Page page = renderer.openPage(i);
                assertTrue("page " + i + " width", page.getWidth() > 0);
                assertTrue("page " + i + " height", page.getHeight() > 0);
                Bitmap bitmap = Bitmap.createBitmap(
                        page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                bitmap.recycle();
                page.close();
            }
        }
    }

    /** An unsupported codec only fails once render() actually invokes it -- open/getPageCount succeed regardless. */
    private void assertRejectedAtRenderNotOpen(String assetName) throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open(assetName);
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            assertEquals(RGB_PAGE_COUNT, renderer.getPageCount());
            TiffRenderer.Page page = renderer.openPage(0); // must not throw
            assertTrue(page.getWidth() > 0);
            assertTrue(page.getHeight() > 0);
            Bitmap bitmap = Bitmap.createBitmap(
                    page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                assertThrows(IOException.class, () -> page.render(
                        bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY));
            } finally {
                bitmap.recycle();
                page.close();
            }
        }
    }

    // --- Supported (built into libtiff itself, or built against the NDK's bundled zlib) -------

    @Test
    public void uncompressed_decodesAllPages() throws IOException {
        assertDecodesAllPages("supported_uncompressed.tif", RGB_PAGE_COUNT);
    }

    @Test
    public void lzw_decodesAllPages() throws IOException {
        assertDecodesAllPages("supported_lzw.tif", RGB_PAGE_COUNT);
    }

    @Test
    public void packBits_decodesAllPages() throws IOException {
        assertDecodesAllPages("supported_packbits.tif", RGB_PAGE_COUNT);
    }

    @Test
    public void deflate_decodesAllPages() throws IOException {
        assertDecodesAllPages("supported_deflate.tif", RGB_PAGE_COUNT);
    }

    @Test
    public void ccittGroup4_decodesAllPages() throws IOException {
        assertDecodesAllPages("supported_ccittg4.tif", 1);
    }

    @Test
    public void jpeg_decodesAllPages() throws IOException {
        assertDecodesAllPages("supported_jpeg.tif", RGB_PAGE_COUNT);
    }

    @Test
    public void webp_decodesAllPages() throws IOException {
        assertDecodesAllPages("supported_webp.tif", RGB_PAGE_COUNT);
    }

    // --- Unsupported (codec disabled at build time -- see CMakeLists.txt) ---------------------

    @Test
    public void zstd_rejectedAtRenderNotOpen() throws IOException {
        assertRejectedAtRenderNotOpen("unsupported_zstd.tif");
    }

    @Test
    public void lzma_rejectedAtRenderNotOpen() throws IOException {
        assertRejectedAtRenderNotOpen("unsupported_lzma.tif");
    }

    @Test
    public void lerc_rejectedAtRenderNotOpen() throws IOException {
        assertRejectedAtRenderNotOpen("unsupported_lerc.tif");
    }

    // --- Non-uniform layouts ---------------------------------------------------------------

    /** Each page has a *different* ImageWidth/ImageLength -- catches bugs assuming uniform size. */
    @Test
    public void varyingPageDimensions_decodesEachPageAtItsOwnSize() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("varying_page_dimensions.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            int[][] expectedSizes = {{10, 10}, {20, 15}, {8, 40}};
            for (int i = 0; i < expectedSizes.length; i++) {
                TiffRenderer.Page page = renderer.openPage(i);
                assertEquals(expectedSizes[i][0], page.getWidth());
                assertEquals(expectedSizes[i][1], page.getHeight());
                Bitmap bitmap = Bitmap.createBitmap(
                        page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                bitmap.recycle();
                page.close();
            }
        }
    }

    /** RGB + an associated-alpha extra sample -- exercises SamplesPerPixel=4 decoding. */
    @Test
    public void rgbaAssociatedAlpha_decodesWithoutThrowing() throws IOException {
        assertDecodesAllPages("rgba_associated_alpha.tif", 1);
    }
}
