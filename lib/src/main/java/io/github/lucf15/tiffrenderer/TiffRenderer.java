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
 * <p>
 * This class enables rendering a TIFF document, one directory ("page") at a time. Android has no
 * built-in TIFF decoder that exposes multi-page/multi-directory rendering the way
 * {@code android.graphics.pdf.PdfRenderer} does for PDF, so this class fills that gap on top of
 * libtiff, compiled for Android via the NDK. The public API intentionally mirrors
 * {@code android.graphics.pdf.PdfRenderer} so it feels familiar to Android developers who have
 * used that class.
 * </p>
 * <p>
 * This class is not thread safe. If you want to render a TIFF, you create a renderer and for
 * every page you want to render, you open the page, render it, and close the page. After you are
 * done with rendering, you close the renderer. After the renderer is closed it should not be used
 * anymore. Pages are rendered one by one — only a single page may be open at any given time.
 * </p>
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

    /**
     * All calls into the native libtiff wrapper are serialized through this lock. libtiff's
     * directory cursor is per-{@code TIFF*} handle, but its error/warning handler state is
     * process global, so — exactly like {@code android.graphics.pdf.PdfRenderer} does for
     * pdfium — we don't allow concurrent native calls even across different renderer instances.
     */
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
     * Creates a new instance.
     * <p>
     * <strong>Note:</strong> The provided file descriptor must be <strong>seekable</strong>, i.e.
     * its data being randomly accessed, e.g. pointing to a file. After finishing with this class
     * you must call {@link #close()}.
     * </p>
     * <p>
     * <strong>Note:</strong> This class takes ownership of the passed in file descriptor and is
     * responsible for closing it when the renderer is closed.
     * </p>
     * <p>
     * If the file is from an untrusted source it is recommended to run the renderer in a
     * separate, isolated process with minimal permissions to limit the impact of security
     * exploits — libtiff has a history of parser vulnerabilities on malformed input.
     * </p>
     *
     * @param input Seekable file descriptor to read from.
     * @throws IOException If an error occurs while reading the file, or it is not a TIFF this
     *         library can decode.
     */
    public TiffRenderer(@NonNull ParcelFileDescriptor input) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");

        final long size;
        try {
            Os.lseek(input.getFileDescriptor(), 0, OsConstants.SEEK_SET);
            size = Os.fstat(input.getFileDescriptor()).st_size;
        } catch (ErrnoException ee) {
            throw new IllegalArgumentException("file descriptor not seekable", ee);
        }
        mInput = input;

        synchronized (sTiffLock) {
            mNativeDocument = TiffRendererNative.nativeOpen(mInput.getFd(), size);
            try {
                mPageCount = TiffRendererNative.nativeGetPageCount(mNativeDocument);
            } catch (Throwable t) {
                TiffRendererNative.nativeClose(mNativeDocument);
                mNativeDocument = 0;
                throw t;
            }
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
     * Opens a page for rendering.
     * <p>
     * <strong>Note:</strong> unlike {@code android.graphics.pdf.PdfRenderer#openPage}, this can
     * throw {@link IOException} — a TIFF directory can fail to seek to (corrupt/truncated file)
     * even though the document as a whole opened successfully, which has no equivalent failure
     * mode in a fully-parsed-up-front PDF document.
     * </p>
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

    /**
     * This class represents a TIFF document page (directory) for rendering.
     */
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

        /**
         * Gets the page index.
         *
         * @return The index.
         */
        public int getIndex() {
            return mIndex;
        }

        /**
         * Gets the page width in pixels, as stored in the TIFF's
         * {@code ImageWidth} tag for this directory.
         *
         * @return The width in pixels.
         */
        public int getWidth() {
            return mWidth;
        }

        /**
         * Gets the page height in pixels, as stored in the TIFF's
         * {@code ImageLength} tag for this directory.
         *
         * @return The height in pixels.
         */
        public int getHeight() {
            return mHeight;
        }

        /**
         * Opts this page into caching its fully-decoded raster, so repeated {@link #render}
         * calls against it — e.g. rendering successive zoom tiles of the same page — reuse the
         * same decode instead of re-running {@code TIFFReadRGBAImageOriented} from scratch every
         * time.
         * <p>
         * <strong>Note:</strong> the cached raster is the page's full, uncompressed pixel grid —
         * for a large scanned page (e.g. 10000&times;10000) that's on the order of hundreds of
         * megabytes. This is <strong>not</strong> enabled by default; only call it if you know
         * you'll render this page more than once. The cache is released automatically when this
         * page is closed.
         * </p>
         *
         * @throws IOException If the page's compression scheme isn't supported by this build, or
         *         the page data is otherwise corrupt.
         */
        public void retainRaster() throws IOException {
            throwIfClosed();
            synchronized (sTiffLock) {
                TiffRendererNative.nativeRetainRaster(mNativeDocument, mIndex);
            }
            mRasterRetained = true;
        }

        /**
         * Renders a page to a bitmap.
         * <p>
         * Each call fully decodes this page's raster unless {@link #retainRaster()} was called
         * first, in which case the decode from that call is reused — useful when rendering the
         * same page multiple times, e.g. one call per zoom tile.
         * </p>
         * <p>
         * You may optionally specify a rectangular clip in the bitmap bounds. No rendering
         * outside the clip will be performed, hence it is your responsibility to initialize the
         * bitmap outside the clip.
         * </p>
         * <p>
         * You may optionally specify a matrix to transform the content from page pixel
         * coordinates to bitmap coordinates. If this matrix is not provided this method will
         * apply a transformation that will fit the whole page to the destination clip if
         * provided, or the destination bitmap if no clip is provided.
         * </p>
         * <p>
         * Unlike a vector PDF renderer, TIFF is a raster format: the source page is already a
         * fixed-resolution pixel grid, so a transform other than fit-to-clip performs image
         * resampling (nearest-neighbor) rather than re-rasterizing vector content.
         * </p>
         * <p>
         * <strong>Note: </strong> The destination bitmap format must be
         * {@link Config#ARGB_8888 ARGB_8888}.
         * </p>
         * <p>
         * <strong>Note: </strong> The optional transformation matrix must be affine as per
         * {@link Matrix#isAffine() Matrix.isAffine()}.
         * </p>
         *
         * <p>
         * <strong>Note:</strong> unlike {@code android.graphics.pdf.PdfRenderer.Page#render},
         * this can throw {@link IOException}: a directory can carry a compression scheme this
         * build of libtiff wasn't compiled with support for (see the README's codec support
         * table), which only surfaces once decoding is actually attempted here — the directory
         * itself opens fine, since the compression tag is just metadata until you try to decode
         * pixels with it.
         * </p>
         *
         * @param destination Destination bitmap to which to render.
         * @param destClip Optional clip in the bitmap bounds.
         * @param transform Optional transformation to apply when rendering.
         * @param renderMode The render mode.
         * @throws IOException If the page's compression scheme isn't supported by this build, or
         *         the page data is otherwise corrupt.
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
