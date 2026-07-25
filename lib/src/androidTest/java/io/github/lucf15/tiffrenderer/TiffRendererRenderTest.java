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

package io.github.lucf15.tiffrenderer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * {@link TiffRenderer.Page#render}'s argument validation and pixel-level correctness. Uses
 * {@code single_page_rgb.tif} (32x24, solid RGB) throughout -- a flat color keeps pixel
 * assertions exact regardless of nearest-neighbor vs. bilinear resampling.
 */
@RunWith(AndroidJUnit4.class)
public class TiffRendererRenderTest {

    private static final int PAGE_WIDTH = 32;
    private static final int PAGE_HEIGHT = 24;
    private static final int PAGE_COLOR = Color.rgb(128, 64, 200);

    private ParcelFileDescriptor openSinglePage() throws IOException {
        return TestAssets.open("single_page_rgb.tif");
    }

    @Test
    public void render_nullDestination_throwsNullPointerException() throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            assertThrows(NullPointerException.class,
                    () -> page.render(null, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY));
            page.close();
        }
    }

    @Test
    public void render_wrongBitmapConfig_throwsIllegalArgumentException() throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            Bitmap bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.RGB_565);
            assertThrows(IllegalArgumentException.class, () -> page.render(
                    bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY));
            page.close();
        }
    }

    @Test
    public void render_destClipExceedsBitmapBounds_throwsIllegalArgumentException()
            throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
            Rect tooWide = new Rect(0, 0, 20, 5);
            assertThrows(IllegalArgumentException.class, () -> page.render(
                    bitmap, tooWide, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY));
            page.close();
        }
    }

    @Test
    public void render_destClipDegenerate_throwsIllegalArgumentException() throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
            Rect empty = new Rect(5, 5, 5, 8); // left == right
            assertThrows(IllegalArgumentException.class, () -> page.render(
                    bitmap, empty, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY));
            page.close();
        }
    }

    @Test
    public void render_nonAffineTransform_throwsIllegalArgumentException() throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            Bitmap bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888);
            Matrix nonAffine = new Matrix();
            float[] values = new float[9];
            nonAffine.getValues(values);
            values[6] = 0.001f; // MPERSP_0 -- nonzero makes Matrix#isAffine() false
            nonAffine.setValues(values);
            assertFalse(nonAffine.isAffine());
            assertThrows(IllegalArgumentException.class, () -> page.render(
                    bitmap, null, nonAffine, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY));
            page.close();
        }
    }

    @Test
    public void render_invalidRenderMode_throwsIllegalArgumentException() throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            Bitmap bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888);
            assertThrows(IllegalArgumentException.class,
                    () -> page.render(bitmap, null, null, 99));
            page.close();
        }
    }

    @Test
    public void render_afterPageClosed_throwsIllegalStateException() throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            page.close();
            Bitmap bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888);
            assertThrows(IllegalStateException.class, () -> page.render(
                    bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY));
        }
    }

    @Test
    public void render_defaultTransform_fillsBitmapWithExactPageColor() throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            Bitmap bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            assertEquals(PAGE_COLOR, bitmap.getPixel(0, 0));
            assertEquals(PAGE_COLOR, bitmap.getPixel(PAGE_WIDTH - 1, PAGE_HEIGHT - 1));
            assertEquals(255, Color.alpha(bitmap.getPixel(PAGE_WIDTH / 2, PAGE_HEIGHT / 2)));
            page.close();
        }
    }

    @Test
    public void render_nearestVsBilinearRenderMode_bothExactForFlatColor() throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            Bitmap bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888);
            page.render(bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_PRINT);

            assertEquals(PAGE_COLOR, bitmap.getPixel(0, 0));
            assertEquals(PAGE_COLOR, bitmap.getPixel(PAGE_WIDTH - 1, PAGE_HEIGHT - 1));
            page.close();
        }
    }

    @Test
    public void render_withDestClip_leavesOutsideClipUntouched() throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            Bitmap bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888);
            int sentinel = Color.YELLOW;
            bitmap.eraseColor(sentinel);

            Rect clip = new Rect(10, 10, 20, 18);
            page.render(bitmap, clip, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            assertEquals(PAGE_COLOR, bitmap.getPixel(15, 14));
            assertEquals(sentinel, bitmap.getPixel(5, 5));
            assertEquals(sentinel, bitmap.getPixel(9, 14));
            assertEquals(sentinel, bitmap.getPixel(20, 14));
            assertEquals(sentinel, bitmap.getPixel(15, 9));
            assertEquals(sentinel, bitmap.getPixel(15, 18));
            page.close();
        }
    }

    @Test
    public void render_withCustomTranslateTransform_offsetsContentAsSpecified() throws IOException {
        try (ParcelFileDescriptor pfd = openSinglePage();
                TiffRenderer renderer = new TiffRenderer(pfd)) {
            TiffRenderer.Page page = renderer.openPage(0);
            Bitmap bitmap = Bitmap.createBitmap(50, 40, Bitmap.Config.ARGB_8888);
            int sentinel = Color.BLUE;
            bitmap.eraseColor(sentinel);

            Matrix identityTranslated = new Matrix();
            identityTranslated.postTranslate(5, 5);
            page.render(bitmap, null, identityTranslated, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            assertEquals(sentinel, bitmap.getPixel(0, 0));
            assertEquals(PAGE_COLOR, bitmap.getPixel(5, 5));
            assertEquals(PAGE_COLOR, bitmap.getPixel(5 + PAGE_WIDTH - 1, 5 + PAGE_HEIGHT - 1));
            page.close();
        }
    }
}
