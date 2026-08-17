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

#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <tiffio.h>

#include <cmath>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <new>
#include <stdexcept>
#include <string>
#include <vector>

#include "affine.h"
#include "tiff_io.h"

#define LOG_TAG "TiffRendererJNI"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace tiffrenderer {

namespace {

// The other legal value from Java, RENDER_MODE_FOR_PRINT (2), selects the nearest-neighbor path below.
constexpr jint kRenderModeForDisplay = 1;

// libtiff reports errors via a process-wide callback, not a return code; all calls are serialized under TiffRenderer.sTiffLock so one scratch buffer suffices.
thread_local std::string gLastError;

void errorHandler(const char* module, const char* fmt, va_list args) {
    char buf[512];
    vsnprintf(buf, sizeof(buf), fmt, args);
    // Never let a std::bad_alloc here unwind through libtiff's C call frames.
    try {
        gLastError = buf;
    } catch (const std::exception&) {
    }
    ALOGE("%s: %s", module != nullptr ? module : "libtiff", buf);
}

void warningHandler(const char* module, const char* fmt, va_list args) {
    char buf[512];
    vsnprintf(buf, sizeof(buf), fmt, args);
    // DEBUG, not WARN -- libtiff warns routinely on perfectly ordinary real-world TIFFs.
    ALOGD("%s: %s", module != nullptr ? module : "libtiff", buf);
}

void throwException(JNIEnv* env, const char* className, const std::string& message) {
    jclass clazz = env->FindClass(className);
    if (clazz == nullptr) {
        return;  // FindClass already threw NoClassDefFoundError.
    }
    env->ThrowNew(clazz, message.c_str());
    env->DeleteLocalRef(clazz);
}

void throwIOException(JNIEnv* env, const std::string& fallbackMessage) {
    throwException(env, "java/io/IOException",
            gLastError.empty() ? fallbackMessage : gLastError);
}

void throwIllegalState(JNIEnv* env, const std::string& message) {
    throwException(env, "java/lang/IllegalStateException", message);
}

// Holds the TIFF* plus an optional single-page raster cache for Page#retainRaster().
struct TiffDocument {
    explicit TiffDocument(TIFF* t) : tiff(t) {}

    TIFF* tiff;
    int cachedPageIndex = -1;
    uint32_t cachedWidth = 0;
    uint32_t cachedHeight = 0;
    std::vector<uint32_t> cachedRaster;
};

TiffDocument* asDocument(jlong documentPtr) {
    return reinterpret_cast<TiffDocument*>(static_cast<intptr_t>(documentPtr));
}

// documentPtr == 0 means a caller bypassed the Java wrapper (e.g. via reflection); throw instead of segfaulting.
TiffDocument* requireDocument(JNIEnv* env, jlong documentPtr) {
    if (documentPtr == 0) {
        throwIllegalState(env, "TIFF document is not open");
        return nullptr;
    }
    return asDocument(documentPtr);
}

// Seeks to pageIndex and decodes it to a packed-RGBA raster; caller must gLastError.clear() first.
bool decodePageOrThrow(JNIEnv* env, TIFF* tiff, jint pageIndex, uint32_t* outWidth,
        uint32_t* outHeight, std::vector<uint32_t>* outRaster) {
    if (!TIFFSetDirectory(tiff, static_cast<tdir_t>(pageIndex))) {
        throwIOException(env, "cannot seek to TIFF page");
        return false;
    }
    uint32_t width = 0;
    uint32_t height = 0;
    TIFFGetField(tiff, TIFFTAG_IMAGEWIDTH, &width);
    TIFFGetField(tiff, TIFFTAG_IMAGELENGTH, &height);
    if (width == 0 || height == 0) {
        throwIOException(env, "invalid TIFF page dimensions");
        return false;
    }
    // uint64_t avoids 32-bit size_t wraparound; the cap avoids a huge alloc succeeding via overcommit and OOM-killing later instead of throwing.
    constexpr uint64_t kMaxDecodedPixels = 250'000'000;  // ~1GB packed RGBA
    const uint64_t pixelCount = static_cast<uint64_t>(width) * static_cast<uint64_t>(height);
    if (pixelCount > kMaxDecodedPixels) {
        throwIOException(env, "TIFF page dimensions too large to decode");
        return false;
    }
    try {
        outRaster->resize(static_cast<size_t>(pixelCount));
    } catch (const std::exception&) {
        throwIOException(env, "TIFF page dimensions too large to decode");
        return false;
    }
    if (!TIFFReadRGBAImageOriented(tiff, width, height, outRaster->data(), ORIENTATION_TOPLEFT,
                0)) {
        throwIOException(env, "failed to decode TIFF page");
        return false;
    }
    *outWidth = width;
    *outHeight = height;
    return true;
}

float clampFloat(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

// Used for RENDER_MODE_FOR_PRINT: exact source pixels, no smoothing.
uint32_t sampleNearest(const uint32_t* raster, int srcWidth, int srcHeight, float x, float y) {
    int sx = static_cast<int>(x);
    int sy = static_cast<int>(y);
    if (sx < 0) sx = 0;
    if (sy < 0) sy = 0;
    if (sx >= srcWidth) sx = srcWidth - 1;
    if (sy >= srcHeight) sy = srcHeight - 1;
    return raster[static_cast<size_t>(sy) * srcWidth + sx];
}

// Used for RENDER_MODE_FOR_DISPLAY: smooths zoomed-in blockiness.
uint32_t sampleBilinear(const uint32_t* raster, int srcWidth, int srcHeight, float x, float y) {
    const float fx = x - 0.5f;
    const float fy = y - 0.5f;
    int x0 = static_cast<int>(std::floor(fx));
    int y0 = static_cast<int>(std::floor(fy));
    const float tx = fx - static_cast<float>(x0);
    const float ty = fy - static_cast<float>(y0);
    int x1 = x0 + 1;
    int y1 = y0 + 1;

    auto clamp = [](int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); };
    x0 = clamp(x0, 0, srcWidth - 1);
    x1 = clamp(x1, 0, srcWidth - 1);
    y0 = clamp(y0, 0, srcHeight - 1);
    y1 = clamp(y1, 0, srcHeight - 1);

    // Interpolate premultiplied by alpha to avoid halos at transparent edges; unpremultiply after.
    auto sample = [&](int px, int py, float* r, float* g, float* b, float* a) {
        const uint32_t p = raster[static_cast<size_t>(py) * srcWidth + px];
        *a = static_cast<float>((p >> 24) & 0xff);
        const float scale = *a / 255.0f;
        *r = static_cast<float>(p & 0xff) * scale;
        *g = static_cast<float>((p >> 8) & 0xff) * scale;
        *b = static_cast<float>((p >> 16) & 0xff) * scale;
    };

    float r00, g00, b00, a00;
    float r10, g10, b10, a10;
    float r01, g01, b01, a01;
    float r11, g11, b11, a11;
    sample(x0, y0, &r00, &g00, &b00, &a00);
    sample(x1, y0, &r10, &g10, &b10, &a10);
    sample(x0, y1, &r01, &g01, &b01, &a01);
    sample(x1, y1, &r11, &g11, &b11, &a11);

    auto blend = [&](float v00, float v10, float v01, float v11) {
        const float top = v00 * (1 - tx) + v10 * tx;
        const float bottom = v01 * (1 - tx) + v11 * tx;
        return top * (1 - ty) + bottom * ty;
    };

    const float a = blend(a00, a10, a01, a11);
    float r = blend(r00, r10, r01, r11);
    float g = blend(g00, g10, g01, g11);
    float b = blend(b00, b10, b01, b11);
    if (a > 0.0f) {
        const float invA = 255.0f / a;
        r *= invA;
        g *= invA;
        b *= invA;
    }

    auto toByte = [](float v) { return static_cast<uint32_t>(clampFloat(v + 0.5f, 0.0f, 255.0f)); };
    return toByte(r) | (toByte(g) << 8) | (toByte(b) << 16) | (toByte(a) << 24);
}

jlong nativeOpen(JNIEnv* env, jclass /*clazz*/, jint fd, jlong size) {
    gLastError.clear();
    // Guard the two allocations decodePageOrThrow's resize try/catch doesn't cover.
    TIFF* tiff = nullptr;
    try {
        tiff = openFromFd(fd, size);
    } catch (const std::exception&) {
        throwIOException(env, "out of memory opening TIFF");
        return 0;
    }
    if (tiff == nullptr) {
        throwIOException(env, "cannot open TIFF");
        return 0;
    }
    try {
        auto* doc = new TiffDocument(tiff);
        return static_cast<jlong>(reinterpret_cast<intptr_t>(doc));
    } catch (const std::exception&) {
        closeTiff(tiff);
        throwIOException(env, "out of memory opening TIFF");
        return 0;
    }
}

void nativeClose(JNIEnv* /*env*/, jclass /*clazz*/, jlong documentPtr) {
    if (documentPtr == 0) {
        return;
    }
    TiffDocument* doc = asDocument(documentPtr);
    closeTiff(doc->tiff);
    delete doc;
}

jint nativeGetPageCount(JNIEnv* env, jclass /*clazz*/, jlong documentPtr) {
    TiffDocument* doc = requireDocument(env, documentPtr);
    if (doc == nullptr) {
        return 0;
    }
    return static_cast<jint>(TIFFNumberOfDirectories(doc->tiff));
}

void nativeOpenPage(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex,
        jintArray outSize) {
    TiffDocument* doc = requireDocument(env, documentPtr);
    if (doc == nullptr) {
        return;
    }
    if (outSize == nullptr) {
        throwException(env, "java/lang/IllegalArgumentException", "outSize cannot be null");
        return;
    }
    TIFF* tiff = doc->tiff;
    gLastError.clear();
    if (!TIFFSetDirectory(tiff, static_cast<tdir_t>(pageIndex))) {
        throwIOException(env, "cannot open TIFF page");
        return;
    }

    uint32_t width = 0;
    uint32_t height = 0;
    TIFFGetField(tiff, TIFFTAG_IMAGEWIDTH, &width);
    TIFFGetField(tiff, TIFFTAG_IMAGELENGTH, &height);
    if (width == 0 || height == 0) {
        throwIOException(env, "invalid TIFF page dimensions");
        return;
    }

    const jint size[2] = {static_cast<jint>(width), static_cast<jint>(height)};
    env->SetIntArrayRegion(outSize, 0, 2, size);
}

void nativeRenderPage(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex,
        jobject bitmap, jint clipLeft, jint clipTop, jint clipRight, jint clipBottom,
        jfloatArray matrixValues, jint renderMode) {
    TiffDocument* doc = requireDocument(env, documentPtr);
    if (doc == nullptr) {
        return;
    }
    if (bitmap == nullptr) {
        throwException(env, "java/lang/IllegalArgumentException", "destination cannot be null");
        return;
    }
    if (matrixValues == nullptr || env->GetArrayLength(matrixValues) < 9) {
        throwException(env, "java/lang/IllegalArgumentException",
                "matrixValues must have 9 elements");
        return;
    }
    gLastError.clear();

    // Reuse the retainRaster() cache instead of redecoding if it matches this page.
    uint32_t srcWidth;
    uint32_t srcHeight;
    const uint32_t* srcRaster;
    std::vector<uint32_t> decoded;
    if (doc->cachedPageIndex == pageIndex) {
        srcWidth = doc->cachedWidth;
        srcHeight = doc->cachedHeight;
        srcRaster = doc->cachedRaster.data();
    } else {
        if (!decodePageOrThrow(env, doc->tiff, pageIndex, &srcWidth, &srcHeight, &decoded)) {
            return;
        }
        srcRaster = decoded.data();
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        throwIllegalState(env, "cannot read destination bitmap info");
        return;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        throwException(env, "java/lang/IllegalArgumentException",
                "destination bitmap must be ARGB_8888");
        return;
    }

    // Re-validate against the real bitmap -- never trust caller-supplied clip bounds this far in.
    if (clipLeft < 0 || clipTop < 0 || clipLeft >= clipRight || clipTop >= clipBottom
            || clipRight > static_cast<jint>(info.width)
            || clipBottom > static_cast<jint>(info.height)) {
        throwException(env, "java/lang/IllegalArgumentException",
                "clip bounds outside destination bitmap");
        return;
    }

    jfloat matrix[9];
    env->GetFloatArrayRegion(matrixValues, 0, 9, matrix);
    // Matrix#getValues() order; perspective terms are ignored since Java already rejects non-affine transforms.
    const AffineTransform forward(matrix[0], matrix[1], matrix[2], matrix[3], matrix[4],
            matrix[5]);
    AffineTransform inverse;
    if (!forward.invert(&inverse)) {
        throwException(env, "java/lang/IllegalArgumentException", "transform is not invertible");
        return;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        throwIllegalState(env, "cannot lock destination bitmap");
        return;
    }

    const int dstStride = static_cast<int>(info.stride) / 4;
    auto* dst = reinterpret_cast<uint32_t*>(pixels);
    const int iSrcWidth = static_cast<int>(srcWidth);
    const int iSrcHeight = static_cast<int>(srcHeight);
    const bool bilinear = renderMode == kRenderModeForDisplay;

    for (jint y = clipTop; y < clipBottom; y++) {
        for (jint x = clipLeft; x < clipRight; x++) {
            float srcX;
            float srcY;
            inverse.apply(static_cast<float>(x) + 0.5f, static_cast<float>(y) + 0.5f, &srcX,
                    &srcY);
            if (srcX < 0 || srcY < 0 || srcX >= iSrcWidth || srcY >= iSrcHeight) {
                // Outside the source page: leave the destination pixel untouched (same contract as PdfRenderer.Page#render).
                continue;
            }
            dst[y * dstStride + x] = bilinear
                    ? sampleBilinear(srcRaster, iSrcWidth, iSrcHeight, srcX, srcY)
                    : sampleNearest(srcRaster, iSrcWidth, iSrcHeight, srcX, srcY);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

// Decodes pageIndex now and caches it so subsequent nativeRenderPage calls reuse it; see Page#retainRaster().
void nativeRetainRaster(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex) {
    TiffDocument* doc = requireDocument(env, documentPtr);
    if (doc == nullptr) {
        return;
    }
    gLastError.clear();

    uint32_t width;
    uint32_t height;
    std::vector<uint32_t> raster;
    if (!decodePageOrThrow(env, doc->tiff, pageIndex, &width, &height, &raster)) {
        return;
    }
    doc->cachedRaster = std::move(raster);
    doc->cachedWidth = width;
    doc->cachedHeight = height;
    doc->cachedPageIndex = pageIndex;
}

// Frees whatever nativeRetainRaster cached; safe for Page#close() to call unconditionally.
void nativeReleaseRaster(JNIEnv* /*env*/, jclass /*clazz*/, jlong documentPtr) {
    // No-op on 0, same contract as nativeClose.
    if (documentPtr == 0) {
        return;
    }
    TiffDocument* doc = asDocument(documentPtr);
    doc->cachedPageIndex = -1;
    doc->cachedWidth = 0;
    doc->cachedHeight = 0;
    std::vector<uint32_t>().swap(doc->cachedRaster);  // release the backing memory, not just clear()
}

const JNINativeMethod gMethods[] = {
        {"nativeOpen", "(IJ)J", reinterpret_cast<void*>(nativeOpen)},
        {"nativeClose", "(J)V", reinterpret_cast<void*>(nativeClose)},
        {"nativeGetPageCount", "(J)I", reinterpret_cast<void*>(nativeGetPageCount)},
        {"nativeOpenPage", "(JI[I)V", reinterpret_cast<void*>(nativeOpenPage)},
        {"nativeRenderPage",
                "(JILandroid/graphics/Bitmap;IIII[FI)V",
                reinterpret_cast<void*>(nativeRenderPage)},
        {"nativeRetainRaster", "(JI)V", reinterpret_cast<void*>(nativeRetainRaster)},
        {"nativeReleaseRaster", "(J)V", reinterpret_cast<void*>(nativeReleaseRaster)},
};

}  // namespace

}  // namespace tiffrenderer

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    TIFFSetErrorHandler(tiffrenderer::errorHandler);
    TIFFSetWarningHandler(tiffrenderer::warningHandler);

    jclass clazz = env->FindClass("com/github/lucf15/tiffrenderer/TiffRendererNative");
    if (clazz == nullptr) {
        return JNI_ERR;
    }
    const jint result = env->RegisterNatives(clazz, tiffrenderer::gMethods,
            sizeof(tiffrenderer::gMethods) / sizeof(tiffrenderer::gMethods[0]));
    env->DeleteLocalRef(clazz);
    if (result != JNI_OK) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
