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
import android.graphics.Bitmap.Config;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Decodes and rasterizes TIFF documents page by page, backed by libtiff via JNI; API mirrors {@code android.graphics.pdf.PdfRenderer}.
 * <p>Not thread safe; only one {@link Page} may be open at a time.
 * <pre>
 * TiffRenderer renderer = new TiffRenderer(getSeekableFileDescriptor());
 *
 * final int pageCount = renderer.getPageCount();
 * for (int i = 0; i &lt; pageCount; i++) {
 *     TiffRenderer.Page page = renderer.openPage(i);
 *     page.render(bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY);
 *     page.close();
 * }
 *
 * renderer.close();
 * </pre>
 *
 * @see #close()
 */
public final class TiffRenderer implements AutoCloseable {

    private static final String TAG = "TiffRenderer";

    /** Serializes all native calls -- libtiff's error/warning handler state is process-global. */
    static final Object sTiffLock = new Object();

    private final Rect mTempRect = new Rect();

    private long mNativeDocument;

    private final int mPageCount;

    private ParcelFileDescriptor mInput;

    private Page mCurrentPage;

    private boolean mClosedGuardTripped;

    /** @hide */
    @IntDef({Page.RENDER_MODE_FOR_DISPLAY, Page.RENDER_MODE_FOR_PRINT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RenderMode {}

    /**
     * Creates a new instance over a seekable file descriptor; this class takes ownership and closes it via {@link #close()}. For untrusted input, prefer running the renderer in an isolated process -- libtiff has a history of parser vulnerabilities.
     *
     * @param input Seekable file descriptor to read from.
     * @throws IOException If an error occurs while reading the file, or it is not a TIFF this library can decode.
     */
    public TiffRenderer(@NonNull ParcelFileDescriptor input) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");
        mInput = input;

        try {
            final long size;
            try {
                Os.lseek(mInput.getFileDescriptor(), 0, OsConstants.SEEK_SET);
                size = Os.fstat(mInput.getFileDescriptor()).st_size;
            } catch (ErrnoException ee) {
                throw new IllegalArgumentException("file descriptor not seekable", ee);
            }

            synchronized (sTiffLock) {
                mNativeDocument = TiffRendererNative.nativeOpen(mInput.getFd(), size);
                mPageCount = TiffRendererNative.nativeGetPageCount(mNativeDocument);
            }
        } catch (Throwable t) {
            doClose();
            throw t;
        }
    }

    /**
     * Gets the number of pages (TIFF directories) in the document.
     *
     * @return The page count.
     */
    public int getPageCount() {
        throwIfClosed();
        return mPageCount;
    }

    /**
     * Opens a page for rendering; unlike {@code PdfRenderer#openPage}, can throw {@link IOException} if the directory itself fails to read.
     *
     * @param index The page index.
     * @return A page that can be rendered.
     * @throws IOException If the page directory cannot be read.
     * @see Page#close()
     */
    @NonNull
    public Page openPage(int index) throws IOException {
        throwIfClosed();
        throwIfPageOpened();
        throwIfPageNotInDocument(index);
        mCurrentPage = new Page(index);
        return mCurrentPage;
    }

    /**
     * Closes this renderer. You should not use this instance after this method is called.
     */
    @Override
    public void close() {
        throwIfClosed();
        throwIfPageOpened();
        doClose();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mInput != null && !mClosedGuardTripped) {
                Log.w(TAG, "TiffRenderer.close() was never called");
            }
            doClose();
        } finally {
            super.finalize();
        }
    }

    private void doClose() {
        if (mCurrentPage != null) {
            mCurrentPage.close();
            mCurrentPage = null;
        }
        if (mNativeDocument != 0) {
            synchronized (sTiffLock) {
                TiffRendererNative.nativeClose(mNativeDocument);
            }
            mNativeDocument = 0;
        }
        if (mInput != null) {
            try {
                mInput.close();
            } catch (IOException ignored) {
            }
            mInput = null;
        }
        mClosedGuardTripped = true;
    }

    private void throwIfClosed() {
        if (mInput == null) {
            throw new IllegalStateException("Already closed");
        }
    }

    private void throwIfPageOpened() {
        if (mCurrentPage != null) {
            throw new IllegalStateException("Current page not closed");
        }
    }

    private void throwIfPageNotInDocument(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= mPageCount) {
            throw new IllegalArgumentException("Invalid page index");
        }
    }

    /** A TIFF document page (directory) for rendering. */
    public final class Page implements AutoCloseable {

        /** Mode to render the content for display on a screen. */
        public static final int RENDER_MODE_FOR_DISPLAY = 1;

        /** Mode to render the content for printing. */
        public static final int RENDER_MODE_FOR_PRINT = 2;

        private final int mIndex;
        private final int mWidth;
        private final int mHeight;

        private boolean mPageClosed;
        private boolean mRasterRetained;

        private Page(int index) throws IOException {
            final int[] outSize = new int[2];
            synchronized (sTiffLock) {
                TiffRendererNative.nativeOpenPage(mNativeDocument, index, outSize);
            }
            mIndex = index;
            mWidth = outSize[0];
            mHeight = outSize[1];
        }

        /** @return The page index. */
        public int getIndex() {
            return mIndex;
        }

        /** @return The page width in pixels, from the TIFF's ImageWidth tag. */
        public int getWidth() {
            return mWidth;
        }

        /** @return The page height in pixels, from the TIFF's ImageLength tag. */
        public int getHeight() {
            return mHeight;
        }

        /**
         * Opts this page into caching its decoded raster so repeated {@link #render} calls reuse it instead of redecoding; not on by default since the cache is the page's full pixel grid (hundreds of MB for a large scan), and is released automatically on close.
         *
         * @throws IOException If the page's compression scheme isn't supported by this build, or the page data is otherwise corrupt.
         */
        public void retainRaster() throws IOException {
            throwIfClosed();
            synchronized (sTiffLock) {
                TiffRendererNative.nativeRetainRaster(mNativeDocument, mIndex);
            }
            mRasterRetained = true;
        }

        /**
         * Renders a page to a bitmap, decoding fresh each call unless {@link #retainRaster()} was called first. destClip restricts rendering to that rect (caller must pre-initialize outside it); transform maps page to bitmap coordinates, must be affine, and defaults to fit-to-clip if null. Unlike {@code PdfRenderer.Page#render}, can throw {@link IOException} if the page's compression scheme isn't supported by this build.
         *
         * @param destination Destination bitmap to which to render; must be {@link Config#ARGB_8888 ARGB_8888}.
         * @param destClip Optional clip in the bitmap bounds.
         * @param transform Optional affine transformation to apply when rendering.
         * @param renderMode The render mode.
         * @throws IOException If the page's compression scheme isn't supported by this build, or the page data is otherwise corrupt.
         * @see #RENDER_MODE_FOR_DISPLAY
         * @see #RENDER_MODE_FOR_PRINT
         */
        public void render(@NonNull Bitmap destination, @Nullable Rect destClip,
                @Nullable Matrix transform, @RenderMode int renderMode) throws IOException {
            throwIfClosed();
            Objects.requireNonNull(destination, "destination cannot be null");

            if (destination.getConfig() != Config.ARGB_8888) {
                throw new IllegalArgumentException("Unsupported pixel format, must be ARGB_8888");
            }

            if (destClip != null) {
                if (destClip.left < 0 || destClip.top < 0
                        || destClip.right > destination.getWidth()
                        || destClip.bottom > destination.getHeight()
                        || destClip.left >= destClip.right
                        || destClip.top >= destClip.bottom) {
                    throw new IllegalArgumentException("destClip not in destination bounds");
                }
            }

            if (transform != null && !transform.isAffine()) {
                throw new IllegalArgumentException("transform not affine");
            }

            if (renderMode != RENDER_MODE_FOR_PRINT && renderMode != RENDER_MODE_FOR_DISPLAY) {
                throw new IllegalArgumentException("Unsupported render mode");
            }

            final Rect clip = mTempRect;
            if (destClip != null) {
                clip.set(destClip);
            } else {
                clip.set(0, 0, destination.getWidth(), destination.getHeight());
            }

            Matrix effectiveTransform = transform;
            if (effectiveTransform == null) {
                effectiveTransform = new Matrix();
                effectiveTransform.postScale(
                        (float) clip.width() / getWidth(), (float) clip.height() / getHeight());
                effectiveTransform.postTranslate(clip.left, clip.top);
            }

            final float[] matrixValues = new float[9];
            effectiveTransform.getValues(matrixValues);

            synchronized (sTiffLock) {
                TiffRendererNative.nativeRenderPage(mNativeDocument, mIndex, destination,
                        clip.left, clip.top, clip.right, clip.bottom, matrixValues, renderMode);
            }
        }

        /**
         * Closes this page.
         *
         * @see TiffRenderer#openPage(int)
         */
        @Override
        public void close() {
            throwIfClosed();
            doClose();
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (!mPageClosed) {
                    Log.w(TAG, "TiffRenderer.Page.close() was never called");
                }
                doClose();
            } finally {
                super.finalize();
            }
        }

        private void doClose() {
            if (mRasterRetained) {
                synchronized (sTiffLock) {
                    TiffRendererNative.nativeReleaseRaster(mNativeDocument);
                }
                mRasterRetained = false;
            }
            mPageClosed = true;
            mCurrentPage = null;
        }

        private void throwIfClosed() {
            if (mPageClosed) {
                throw new IllegalStateException("Already closed");
            }
        }
    }
}
