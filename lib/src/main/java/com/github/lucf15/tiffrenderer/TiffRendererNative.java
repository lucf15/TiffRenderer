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

import android.graphics.Bitmap;

import java.io.IOException;

/** Native method declarations for the libtiff JNI wrapper, kept separate from {@link TiffRenderer}. */
final class TiffRendererNative {

    static {
        System.loadLibrary("tiffrenderer_jni");
    }

    private TiffRendererNative() {}

    /** Opens a TIFF via a custom libtiff I/O handler bound to {@code fd}; throws {@link IOException} on decode failure. */
    static native long nativeOpen(int fd, long size) throws IOException;

    static native void nativeClose(long documentPtr);

    static native int nativeGetPageCount(long documentPtr);

    /** Sets the current TIFF directory to {@code pageIndex} and writes {width, height} to outSize. */
    static native void nativeOpenPage(long documentPtr, int pageIndex, int[] outSize)
            throws IOException;

    /** Renders {@code pageIndex} into {@code destination}, clipped and resampled through the affine {@code matrixValues}; throws {@link IOException} if the codec isn't supported. */
    static native void nativeRenderPage(long documentPtr, int pageIndex, Bitmap destination,
            int clipLeft, int clipTop, int clipRight, int clipBottom, float[] matrixValues,
            int renderMode) throws IOException;

    /** Decodes {@code pageIndex} now and caches it so subsequent {@link #nativeRenderPage} calls reuse it. */
    static native void nativeRetainRaster(long documentPtr, int pageIndex) throws IOException;

    /** Frees whatever {@link #nativeRetainRaster} cached, if anything; a no-op otherwise. */
    static native void nativeReleaseRaster(long documentPtr);
}
