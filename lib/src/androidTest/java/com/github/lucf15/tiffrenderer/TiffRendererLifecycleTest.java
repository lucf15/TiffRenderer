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

import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/** {@link TiffRenderer}/{@link TiffRenderer.Page}'s state machine: construction, open/close preconditions, one-page-open-at-a-time. */
@RunWith(AndroidJUnit4.class)
public class TiffRendererLifecycleTest {

    @Test
    public void constructor_nullInput_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new TiffRenderer(null));
    }

    @Test
    public void constructor_notATiffFile_throwsIOException() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("not_a_tiff.bin")) {
            assertThrows(IOException.class, () -> new TiffRenderer(pfd));
        }
    }

    @Test
    public void constructor_validFile_reportsCorrectPageCount() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("varying_page_dimensions.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            assertEquals(3, renderer.getPageCount());
        }
    }

    @Test
    public void getPageCount_afterClose_throwsIllegalStateException() throws IOException {
        TiffRenderer renderer = new TiffRenderer(TestAssets.open("single_page_rgb.tif"));
        renderer.close();
        assertThrows(IllegalStateException.class, renderer::getPageCount);
    }

    @Test
    public void close_calledTwice_throwsIllegalStateException() throws IOException {
        TiffRenderer renderer = new TiffRenderer(TestAssets.open("single_page_rgb.tif"));
        renderer.close();
        assertThrows(IllegalStateException.class, renderer::close);
    }

    @Test
    public void close_whilePageOpen_throwsIllegalStateException() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("single_page_rgb.tif")) {
            TiffRenderer renderer = new TiffRenderer(pfd);
            TiffRenderer.Page page = renderer.openPage(0);
            assertThrows(IllegalStateException.class, renderer::close);
            page.close(); // now safe to actually tear down
            renderer.close();
        }
    }

    @Test
    public void openPage_afterClose_throwsIllegalStateException() throws IOException {
        TiffRenderer renderer = new TiffRenderer(TestAssets.open("single_page_rgb.tif"));
        renderer.close();
        assertThrows(IllegalStateException.class, () -> renderer.openPage(0));
    }

    @Test
    public void openPage_negativeIndex_throwsIllegalArgumentException() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("single_page_rgb.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            assertThrows(IllegalArgumentException.class, () -> renderer.openPage(-1));
        }
    }

    @Test
    public void openPage_indexAtOrPastPageCount_throwsIllegalArgumentException() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("single_page_rgb.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            assertEquals(1, renderer.getPageCount());
            assertThrows(IllegalArgumentException.class, () -> renderer.openPage(1));
        }
    }

    @Test
    public void openPage_whileAnotherPageOpen_throwsIllegalStateException() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("varying_page_dimensions.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            assertThrows(IllegalStateException.class, () -> renderer.openPage(1));
            page.close();
        }
    }

    @Test
    public void openPage_afterPreviousPageClosed_succeeds() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("varying_page_dimensions.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            renderer.openPage(0).close();
            TiffRenderer.Page second = renderer.openPage(1);
            assertEquals(1, second.getIndex());
            second.close();
        }
    }

    @Test
    public void page_close_calledTwice_throwsIllegalStateException() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("single_page_rgb.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            page.close();
            assertThrows(IllegalStateException.class, page::close);
        }
    }

    @Test
    public void page_getIndexWidthHeight_matchPerPageTiffTags() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("varying_page_dimensions.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            int[][] expectedSizes = {{10, 10}, {20, 15}, {8, 40}};
            for (int i = 0; i < expectedSizes.length; i++) {
                TiffRenderer.Page page = renderer.openPage(i);
                assertEquals(i, page.getIndex());
                assertEquals("page " + i + " width", expectedSizes[i][0], page.getWidth());
                assertEquals("page " + i + " height", expectedSizes[i][1], page.getHeight());
                page.close();
            }
        }
    }

    @Test
    public void renderer_finalizeWithoutClose_doesNotThrow() throws Throwable {
        // Callable directly: this test is in the same package as TiffRenderer's protected finalize().
        TiffRenderer renderer = new TiffRenderer(TestAssets.open("single_page_rgb.tif"));
        renderer.finalize();
        assertTrue(true);
    }
}
