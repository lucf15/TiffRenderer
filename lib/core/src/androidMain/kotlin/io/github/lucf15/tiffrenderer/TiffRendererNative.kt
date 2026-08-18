package io.github.lucf15.tiffrenderer

import android.graphics.Bitmap
import java.io.IOException

/** Thin JNI binding to `tiff_renderer_jni.cpp` via `RegisterNatives`. `@JvmStatic` is required,
 * not just idiomatic: without it these compile to instance methods taking a `jobject` receiver,
 * not the static `jclass`-first signatures the C++ side expects. */
internal object TiffRendererNative {
    init {
        System.loadLibrary("tiffrenderer_jni")
    }

    @JvmStatic
    @Throws(IOException::class)
    external fun nativeOpen(fd: Int, size: Long): Long

    @JvmStatic
    external fun nativeClose(documentPtr: Long)

    @JvmStatic
    external fun nativeGetPageCount(documentPtr: Long): Int

    @JvmStatic
    @Throws(IOException::class)
    external fun nativeOpenPage(documentPtr: Long, pageIndex: Int, outSize: IntArray)

    @JvmStatic
    @Throws(IOException::class)
    external fun nativeRenderPage(
        documentPtr: Long,
        pageIndex: Int,
        destination: Bitmap,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
        matrixValues: FloatArray,
        renderMode: Int,
    )

    @JvmStatic
    @Throws(IOException::class)
    external fun nativeRetainRaster(documentPtr: Long, pageIndex: Int)

    @JvmStatic
    external fun nativeReleaseRaster(documentPtr: Long)
}
