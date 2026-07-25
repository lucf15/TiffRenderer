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
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace tiffrenderer {

namespace {

// Mirrors TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY; the only other legal value from Java is
// RENDER_MODE_FOR_PRINT (2), which selects the nearest-neighbor path below.
constexpr jint kRenderModeForDisplay = 1;

// libtiff reports errors/warnings through a process-wide callback rather than a return-code
// enum (unlike pdfium's FPDF_GetLastError()). All native entry points below run under Java's
// TiffRenderer.sTiffLock, so a single scratch buffer — rather than one per TIFF* — is enough to
// carry the most recent message from the handler out to the JNI call that triggered it.
thread_local std::string gLastError;

void errorHandler(const char* module, const char* fmt, va_list args) {
    char buf[512];
    vsnprintf(buf, sizeof(buf), fmt, args);
    gLastError = buf;
    ALOGE("%s: %s", module != nullptr ? module : "libtiff", buf);
}

void warningHandler(const char* module, const char* fmt, va_list args) {
    char buf[512];
    vsnprintf(buf, sizeof(buf), fmt, args);
    ALOGW("%s: %s", module != nullptr ? module : "libtiff", buf);
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

// documentPtr points at one of these, not a bare TIFF*, so the currently-open page's decoded
// raster can optionally outlive a single nativeRenderPage call (see Page#retainRaster). Only one
// page is ever open at a time (enforced in TiffRenderer.java), so a single cache slot — rather
// than a per-page map — is enough.
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

// Shared by the cache-miss path of nativeRenderPage and nativeRetainRaster: seeks to pageIndex's
// directory and fully decodes it to a packed-RGBA raster, throwing the appropriate IOException
// on failure. Caller is responsible for gLastError.clear() beforehand.
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
    // width/height come straight from the file's own tags. Widen to uint64_t before multiplying
    // (size_t is only 32 bits on armeabi-v7a/x86 and would wrap silently) and reject anything
    // past a sane decode size up front: Android's memory overcommit lets a "merely huge"
    // allocation succeed and only crash later via the OOM killer, not a catchable exception.
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

// Nearest-neighbor sample, used for RENDER_MODE_FOR_PRINT: reproduces source pixels exactly
// with no smoothing, which is what you want when the output is going to a printer rather than
// a possibly-zoomed screen.
uint32_t sampleNearest(const uint32_t* raster, int srcWidth, int srcHeight, float x, float y) {
    int sx = static_cast<int>(x);
    int sy = static_cast<int>(y);
    if (sx < 0) sx = 0;
    if (sy < 0) sy = 0;
    if (sx >= srcWidth) sx = srcWidth - 1;
    if (sy >= srcHeight) sy = srcHeight - 1;
    return raster[static_cast<size_t>(sy) * srcWidth + sx];
}

// Bilinear sample, used for RENDER_MODE_FOR_DISPLAY: smooths the zoomed-in blockiness a
// nearest-neighbor sample would otherwise show on screen.
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

    auto channel = [&](int shift) -> float {
        auto at = [&](int px, int py) -> float {
            return static_cast<float>((raster[static_cast<size_t>(py) * srcWidth + px] >> shift) & 0xff);
        };
        const float top = at(x0, y0) * (1 - tx) + at(x1, y0) * tx;
        const float bottom = at(x0, y1) * (1 - tx) + at(x1, y1) * tx;
        return top * (1 - ty) + bottom * ty;
    };

    const auto r = static_cast<uint32_t>(channel(0) + 0.5f);
    const auto g = static_cast<uint32_t>(channel(8) + 0.5f);
    const auto b = static_cast<uint32_t>(channel(16) + 0.5f);
    const auto a = static_cast<uint32_t>(channel(24) + 0.5f);
    return r | (g << 8) | (b << 16) | (a << 24);
}

jlong nativeOpen(JNIEnv* env, jclass /*clazz*/, jint fd, jlong size) {
    gLastError.clear();
    TIFF* tiff = openFromFd(fd, size);
    if (tiff == nullptr) {
        throwIOException(env, "cannot open TIFF");
        return 0;
    }
    auto* doc = new TiffDocument(tiff);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(doc));
}

void nativeClose(JNIEnv* /*env*/, jclass /*clazz*/, jlong documentPtr) {
    if (documentPtr == 0) {
        return;
    }
    TiffDocument* doc = asDocument(documentPtr);
    closeTiff(doc->tiff);
    delete doc;
}

jint nativeGetPageCount(JNIEnv* /*env*/, jclass /*clazz*/, jlong documentPtr) {
    return static_cast<jint>(TIFFNumberOfDirectories(asDocument(documentPtr)->tiff));
}

void nativeOpenPage(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex,
        jintArray outSize) {
    TIFF* tiff = asDocument(documentPtr)->tiff;
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
    TiffDocument* doc = asDocument(documentPtr);
    gLastError.clear();

    // TIFFReadRGBAImageOriented decodes the whole page into a packed-RGBA raster up front.
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

    jfloat matrix[9];
    env->GetFloatArrayRegion(matrixValues, 0, 9, matrix);
    // android.graphics.Matrix#getValues() order: MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y,
    // MSCALE_Y, MTRANS_Y, MPERSP_0, MPERSP_1, MPERSP_2 — perspective terms are ignored because
    // TiffRenderer.Page#render() already rejects non-affine transforms in Java.
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
                // Outside the source page: leave the destination pixel untouched, same
                // "caller must pre-initialize outside the clip" contract as
                // android.graphics.pdf.PdfRenderer.Page#render.
                continue;
            }
            dst[y * dstStride + x] = bilinear
                    ? sampleBilinear(srcRaster, iSrcWidth, iSrcHeight, srcX, srcY)
                    : sampleNearest(srcRaster, iSrcWidth, iSrcHeight, srcX, srcY);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

// Decodes pageIndex now and stashes the raster on documentPtr's TiffDocument so subsequent
// nativeRenderPage calls against the same page reuse it instead of redecoding. Opt-in — see
// TiffRenderer.Page#retainRaster() for why this isn't the default.
void nativeRetainRaster(JNIEnv* env, jclass /*clazz*/, jlong documentPtr, jint pageIndex) {
    TiffDocument* doc = asDocument(documentPtr);
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

// Frees whatever nativeRetainRaster cached, if anything. A no-op when nothing is cached, so it's
// safe for Page#close() to call unconditionally.
void nativeReleaseRaster(JNIEnv* /*env*/, jclass /*clazz*/, jlong documentPtr) {
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

    jclass clazz = env->FindClass("io/github/lucf15/tiffrenderer/TiffRendererNative");
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
