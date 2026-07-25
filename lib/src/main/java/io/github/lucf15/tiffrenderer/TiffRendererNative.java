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

import android.graphics.Bitmap;

import java.io.IOException;

/**
 * Native method declarations for the libtiff JNI wrapper, kept separate from {@link TiffRenderer}
 * so the public class stays free of {@code native}/{@code System.loadLibrary} plumbing.
 */
final class TiffRendererNative {

    static {
        System.loadLibrary("tiffrenderer_jni");
    }

    private TiffRendererNative() {}

    /**
     * Opens a TIFF via a custom libtiff I/O handler bound to {@code fd} (see
     * {@code tiff_io.cpp}). Mirrors the {@code (fd, size)} shape of
     * {@code android.graphics.pdf.PdfRenderer}'s {@code nativeCreate}. Throws
     * {@link java.io.IOException} on decode failure.
     */
    static native long nativeOpen(int fd, long size) throws IOException;

    static native void nativeClose(long documentPtr);

    static native int nativeGetPageCount(long documentPtr);

    /** Sets the current TIFF directory to {@code pageIndex} and writes {width, height} to outSize. */
    static native void nativeOpenPage(long documentPtr, int pageIndex, int[] outSize)
            throws IOException;

    /**
     * Renders {@code pageIndex}'s raster into {@code destination} (an {@code ARGB_8888} bitmap),
     * clipped to [clipLeft, clipTop, clipRight, clipBottom) and resampled through the affine
     * {@code matrixValues} ({@link android.graphics.Matrix#getValues(float[])} order: MSCALE_X,
     * MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y, ...). Throws {@link IOException} if
     * {@code pageIndex}'s compression scheme wasn't compiled into this build of libtiff.
     */
    static native void nativeRenderPage(long documentPtr, int pageIndex, Bitmap destination,
            int clipLeft, int clipTop, int clipRight, int clipBottom, float[] matrixValues,
            int renderMode) throws IOException;

    /**
     * Decodes {@code pageIndex} now and caches the raster natively, so subsequent
     * {@link #nativeRenderPage} calls against the same page reuse it instead of redecoding.
     * Throws {@link IOException} under the same conditions as {@link #nativeRenderPage}.
     */
    static native void nativeRetainRaster(long documentPtr, int pageIndex) throws IOException;

    /** Frees whatever {@link #nativeRetainRaster} cached, if anything; a no-op otherwise. */
    static native void nativeReleaseRaster(long documentPtr);
}
