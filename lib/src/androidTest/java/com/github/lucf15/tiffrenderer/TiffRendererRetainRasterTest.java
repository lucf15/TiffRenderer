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

/** {@link TiffRenderer.Page#retainRaster()}'s decode-once, render-many opt-in caching. */
@RunWith(AndroidJUnit4.class)
public class TiffRendererRetainRasterTest {

    /** Repeated render() calls against the same open page must keep producing correct output. */
    @Test
    public void repeatedRenders_produceIdenticalOutput() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("supported_lzw.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            page.retainRaster();

            Bitmap first = Bitmap.createBitmap(
                    page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
            page.render(first, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            Bitmap second = Bitmap.createBitmap(
                    page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
            page.render(second, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            assertTrue("repeated render() with retainRaster() must produce identical output",
                    first.sameAs(second));

            first.recycle();
            second.recycle();
            page.close();
        }
    }

    /** A codec this build can't decode must fail loudly from retainRaster() itself, not the next render(). */
    @Test
    public void unsupportedCodec_rejectedByRetainRasterItself() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("unsupported_zstd.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            try {
                assertThrows(IOException.class, page::retainRaster);
            } finally {
                page.close();
            }
        }
    }

    /** Closing a page that opted into retainRaster() must not leak the cache into the next page. */
    @Test
    public void releasedOnPageClose_doesNotLeakIntoNextPage() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("varying_page_dimensions.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page firstPage = renderer.openPage(0); // 10x10
            firstPage.retainRaster();
            Bitmap firstBitmap = Bitmap.createBitmap(
                    firstPage.getWidth(), firstPage.getHeight(), Bitmap.Config.ARGB_8888);
            firstPage.render(firstBitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            firstBitmap.recycle();
            firstPage.close();

            // Page 2 is a different size (20x15 vs 10x10) -- a leaked cache would crash or misdecode here.
            TiffRenderer.Page secondPage = renderer.openPage(1);
            assertEquals(20, secondPage.getWidth());
            assertEquals(15, secondPage.getHeight());
            Bitmap secondBitmap = Bitmap.createBitmap(
                    secondPage.getWidth(), secondPage.getHeight(), Bitmap.Config.ARGB_8888);
            secondPage.render(secondBitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            secondBitmap.recycle();
            secondPage.close();
        }
    }

    @Test
    public void retainRaster_afterPageClosed_throwsIllegalStateException() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("single_page_rgb.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            page.close();
            assertThrows(IllegalStateException.class, page::retainRaster);
        }
    }
}
