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

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/** Hostile/corrupt TIFFs (adversarial dimensions, truncated data) must surface as {@link IOException}, never a crash. */
@RunWith(AndroidJUnit4.class)
public class TiffRendererCorruptInputTest {

    @Test
    public void constructor_garbageBytes_throwsIOException() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("not_a_tiff.bin")) {
            assertThrows(IOException.class, () -> new TiffRenderer(pfd));
        }
    }

    /** huge_dimensions.tif claims a valid-but-100000x100000 page; render() must reject the ~40GB allocation, not crash. */
    @Test
    public void render_hugeDimensionsPage_throwsIOExceptionInsteadOfCrashing() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("huge_dimensions.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            assertEquals(100000, page.getWidth());
            assertEquals(100000, page.getHeight());

            Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
            try {
                assertThrows(IOException.class, () -> page.render(
                        bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY));
            } finally {
                bitmap.recycle();
                page.close();
            }
        }
    }

    /** Same allocation-failure path, reached through retainRaster() instead of render(). */
    @Test
    public void retainRaster_hugeDimensionsPage_throwsIOExceptionInsteadOfCrashing()
            throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("huge_dimensions.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            try {
                assertThrows(IOException.class, page::retainRaster);
            } finally {
                page.close();
            }
        }
    }

    /** truncated.tif is missing the last 40 bytes of its strip data -- a real short read, not a dimensions problem. */
    @Test
    public void render_truncatedFile_throwsIOException() throws IOException {
        try (ParcelFileDescriptor pfd = TestAssets.open("truncated.tif");
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0); // succeeds -- IFD itself is intact
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
}
