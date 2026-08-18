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

#include "tiff_core.h"

#include <tiffio.h>

#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <new>
#include <string>
#include <vector>

#include "affine.h"
#include "tiff_io.h"

#if defined(__ANDROID__)
#include <android/log.h>
#define TIFFCORE_LOG_TAG "TiffCore"
#define TIFFCORE_LOGE(fmt, ...) \
    __android_log_print(ANDROID_LOG_ERROR, TIFFCORE_LOG_TAG, fmt, ##__VA_ARGS__)
#define TIFFCORE_LOGD(fmt, ...) \
    __android_log_print(ANDROID_LOG_DEBUG, TIFFCORE_LOG_TAG, fmt, ##__VA_ARGS__)
#else
#define TIFFCORE_LOGE(fmt, ...) fprintf(stderr, "[TiffCore ERROR] " fmt "\n", ##__VA_ARGS__)
#define TIFFCORE_LOGD(fmt, ...) fprintf(stderr, "[TiffCore DEBUG] " fmt "\n", ##__VA_ARGS__)
#endif

namespace {

// libtiff reports errors via a process-wide callback, not a return code; every entry point here
// is expected to be externally serialized by the caller (mirrors TiffRenderer.sTiffLock), so one
// thread_local scratch buffer suffices.
thread_local std::string gLastError;

void errorHandler(const char* module, const char* fmt, va_list args) {
    char buf[512];
    vsnprintf(buf, sizeof(buf), fmt, args);
    // Never let a std::bad_alloc here unwind through libtiff's C call frames.
    try {
        gLastError = buf;
    } catch (const std::exception&) {
    }
    TIFFCORE_LOGE("%s: %s", module != nullptr ? module : "libtiff", buf);
}

void warningHandler(const char* module, const char* fmt, va_list args) {
    char buf[512];
    vsnprintf(buf, sizeof(buf), fmt, args);
    // DEBUG, not error-level: libtiff warns routinely on perfectly ordinary real-world TIFFs.
    TIFFCORE_LOGD("%s: %s", module != nullptr ? module : "libtiff", buf);
}

void fillErrBuf(char* errBuf, size_t errBufLen, const char* fallbackMessage) {
    if (errBuf == nullptr || errBufLen == 0) {
        return;
    }
    const std::string& message = gLastError.empty() ? std::string(fallbackMessage) : gLastError;
    std::snprintf(errBuf, errBufLen, "%s", message.c_str());
}

float clampFloat(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

// Used for TIFFCORE_RENDER_MODE_PRINT: exact source pixels, no smoothing.
uint32_t sampleNearest(const uint32_t* raster, int srcWidth, int srcHeight, float x, float y) {
    int sx = static_cast<int>(x);
    int sy = static_cast<int>(y);
    if (sx < 0) sx = 0;
    if (sy < 0) sy = 0;
    if (sx >= srcWidth) sx = srcWidth - 1;
    if (sy >= srcHeight) sy = srcHeight - 1;
    return raster[static_cast<size_t>(sy) * srcWidth + sx];
}

// Used for TIFFCORE_RENDER_MODE_DISPLAY: smooths zoomed-in blockiness.
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

}  // namespace

// Holds the TIFF* plus an optional single-page raster cache for tiffcore_retain_raster().
// customIo distinguishes the two ways tiff was opened: tiffcore_open's TIFFClientOpen bound to a
// raw fd (client data is a tiffrenderer::FdHandle*, freed by tiffrenderer::closeTiff) vs.
// tiffcore_open_path[_w]'s plain TIFFOpen/TIFFOpenW (libtiff owns its own stdio state; a plain
// TIFFClose is correct and closeTiff would misinterpret the client data as an FdHandle*).
struct TiffCoreDocument {
    TiffCoreDocument(TIFF* t, bool customIo) : tiff(t), customIo(customIo) {}

    TIFF* tiff;
    bool customIo;
    int32_t cachedPageIndex = -1;
    uint32_t cachedWidth = 0;
    uint32_t cachedHeight = 0;
    std::vector<uint32_t> cachedRaster;
};

namespace {

// Seeks to pageIndex and decodes it to a packed-RGBA raster; caller must gLastError.clear() first.
bool decodePage(TIFF* tiff, int32_t pageIndex, uint32_t* outWidth, uint32_t* outHeight,
        std::vector<uint32_t>* outRaster, char* errBuf, size_t errBufLen) {
    if (!TIFFSetDirectory(tiff, static_cast<tdir_t>(pageIndex))) {
        fillErrBuf(errBuf, errBufLen, "cannot seek to TIFF page");
        return false;
    }
    uint32_t width = 0;
    uint32_t height = 0;
    TIFFGetField(tiff, TIFFTAG_IMAGEWIDTH, &width);
    TIFFGetField(tiff, TIFFTAG_IMAGELENGTH, &height);
    if (width == 0 || height == 0) {
        fillErrBuf(errBuf, errBufLen, "invalid TIFF page dimensions");
        return false;
    }
    // uint64_t avoids 32-bit size_t wraparound; the cap avoids a huge alloc succeeding via
    // overcommit and OOM-killing later instead of failing cleanly.
    constexpr uint64_t kMaxDecodedPixels = 250'000'000;  // ~1GB packed RGBA
    const uint64_t pixelCount = static_cast<uint64_t>(width) * static_cast<uint64_t>(height);
    if (pixelCount > kMaxDecodedPixels) {
        fillErrBuf(errBuf, errBufLen, "TIFF page dimensions too large to decode");
        return false;
    }
    try {
        outRaster->resize(static_cast<size_t>(pixelCount));
    } catch (const std::exception&) {
        fillErrBuf(errBuf, errBufLen, "TIFF page dimensions too large to decode");
        return false;
    }
    if (!TIFFReadRGBAImageOriented(tiff, width, height, outRaster->data(), ORIENTATION_TOPLEFT,
                0)) {
        fillErrBuf(errBuf, errBufLen, "failed to decode TIFF page");
        return false;
    }
    *outWidth = width;
    *outHeight = height;
    return true;
}

}  // namespace

void tiffcore_global_init(void) {
    TIFFSetErrorHandler(errorHandler);
    TIFFSetWarningHandler(warningHandler);
}

namespace {

TiffCoreStatus wrapOpenedTiff(TIFF* tiff, bool customIo, TiffCoreDocument** outDoc, char* errBuf,
        size_t errBufLen) {
    if (tiff == nullptr) {
        fillErrBuf(errBuf, errBufLen, "cannot open TIFF");
        return TIFFCORE_ERROR_IO;
    }
    try {
        *outDoc = new TiffCoreDocument(tiff, customIo);
        return TIFFCORE_OK;
    } catch (const std::exception&) {
        if (customIo) {
            tiffrenderer::closeTiff(tiff);
        } else {
            TIFFClose(tiff);
        }
        fillErrBuf(errBuf, errBufLen, "out of memory opening TIFF");
        return TIFFCORE_ERROR_IO;
    }
}

}  // namespace

TiffCoreStatus tiffcore_open(int fd, int64_t size, TiffCoreDocument** outDoc, char* errBuf,
        size_t errBufLen) {
    gLastError.clear();
    TIFF* tiff = nullptr;
    try {
        tiff = tiffrenderer::openFromFd(fd, size);
    } catch (const std::exception&) {
        fillErrBuf(errBuf, errBufLen, "out of memory opening TIFF");
        return TIFFCORE_ERROR_IO;
    }
    return wrapOpenedTiff(tiff, /*customIo=*/true, outDoc, errBuf, errBufLen);
}

TiffCoreStatus tiffcore_open_path(const char* utf8Path, TiffCoreDocument** outDoc, char* errBuf,
        size_t errBufLen) {
    gLastError.clear();
    TIFF* tiff = TIFFOpen(utf8Path, "r");
    return wrapOpenedTiff(tiff, /*customIo=*/false, outDoc, errBuf, errBufLen);
}

#ifdef _WIN32
TiffCoreStatus tiffcore_open_path_w(const wchar_t* utf16Path, TiffCoreDocument** outDoc,
        char* errBuf, size_t errBufLen) {
    gLastError.clear();
    TIFF* tiff = TIFFOpenW(utf16Path, "r");
    return wrapOpenedTiff(tiff, /*customIo=*/false, outDoc, errBuf, errBufLen);
}
#endif

void tiffcore_close(TiffCoreDocument* doc) {
    if (doc == nullptr) {
        return;
    }
    if (doc->customIo) {
        tiffrenderer::closeTiff(doc->tiff);
    } else {
        TIFFClose(doc->tiff);
    }
    delete doc;
}

int32_t tiffcore_get_page_count(TiffCoreDocument* doc) {
    if (doc == nullptr) {
        return 0;
    }
    return static_cast<int32_t>(TIFFNumberOfDirectories(doc->tiff));
}

TiffCoreStatus tiffcore_open_page(TiffCoreDocument* doc, int32_t pageIndex, uint32_t* outWidth,
        uint32_t* outHeight, char* errBuf, size_t errBufLen) {
    if (doc == nullptr) {
        fillErrBuf(errBuf, errBufLen, "TIFF document is not open");
        return TIFFCORE_ERROR_ILLEGAL_STATE;
    }
    gLastError.clear();
    TIFF* tiff = doc->tiff;
    if (!TIFFSetDirectory(tiff, static_cast<tdir_t>(pageIndex))) {
        fillErrBuf(errBuf, errBufLen, "cannot open TIFF page");
        return TIFFCORE_ERROR_IO;
    }

    uint32_t width = 0;
    uint32_t height = 0;
    TIFFGetField(tiff, TIFFTAG_IMAGEWIDTH, &width);
    TIFFGetField(tiff, TIFFTAG_IMAGELENGTH, &height);
    if (width == 0 || height == 0) {
        fillErrBuf(errBuf, errBufLen, "invalid TIFF page dimensions");
        return TIFFCORE_ERROR_IO;
    }

    *outWidth = width;
    *outHeight = height;
    return TIFFCORE_OK;
}

TiffCoreStatus tiffcore_retain_raster(TiffCoreDocument* doc, int32_t pageIndex, char* errBuf,
        size_t errBufLen) {
    if (doc == nullptr) {
        fillErrBuf(errBuf, errBufLen, "TIFF document is not open");
        return TIFFCORE_ERROR_ILLEGAL_STATE;
    }
    gLastError.clear();

    uint32_t width;
    uint32_t height;
    std::vector<uint32_t> raster;
    if (!decodePage(doc->tiff, pageIndex, &width, &height, &raster, errBuf, errBufLen)) {
        return TIFFCORE_ERROR_IO;
    }
    doc->cachedRaster = std::move(raster);
    doc->cachedWidth = width;
    doc->cachedHeight = height;
    doc->cachedPageIndex = pageIndex;
    return TIFFCORE_OK;
}

void tiffcore_release_raster(TiffCoreDocument* doc) {
    if (doc == nullptr) {
        return;
    }
    doc->cachedPageIndex = -1;
    doc->cachedWidth = 0;
    doc->cachedHeight = 0;
    std::vector<uint32_t>().swap(doc->cachedRaster);  // release the backing memory, not just clear()
}

TiffCoreStatus tiffcore_render_page(TiffCoreDocument* doc, int32_t pageIndex, uint32_t* dstPixels,
        int32_t dstStridePixels, int32_t dstWidth, int32_t dstHeight, int32_t clipLeft,
        int32_t clipTop, int32_t clipRight, int32_t clipBottom, const float matrix[6],
        TiffCoreRenderMode renderMode, char* errBuf, size_t errBufLen) {
    if (doc == nullptr) {
        fillErrBuf(errBuf, errBufLen, "TIFF document is not open");
        return TIFFCORE_ERROR_ILLEGAL_STATE;
    }
    if (dstPixels == nullptr) {
        fillErrBuf(errBuf, errBufLen, "destination cannot be null");
        return TIFFCORE_ERROR_INVALID_ARG;
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
        if (!decodePage(doc->tiff, pageIndex, &srcWidth, &srcHeight, &decoded, errBuf,
                    errBufLen)) {
            return TIFFCORE_ERROR_IO;
        }
        srcRaster = decoded.data();
    }

    // Never trust caller-supplied clip bounds this far in.
    if (clipLeft < 0 || clipTop < 0 || clipLeft >= clipRight || clipTop >= clipBottom
            || clipRight > dstWidth || clipBottom > dstHeight) {
        fillErrBuf(errBuf, errBufLen, "clip bounds outside destination bitmap");
        return TIFFCORE_ERROR_INVALID_ARG;
    }

    // matrix[] is in android.graphics.Matrix#getValues() order; perspective terms are ignored
    // since the caller has already rejected non-affine transforms.
    const tiffrenderer::AffineTransform forward(matrix[0], matrix[1], matrix[2], matrix[3],
            matrix[4], matrix[5]);
    tiffrenderer::AffineTransform inverse;
    if (!forward.invert(&inverse)) {
        fillErrBuf(errBuf, errBufLen, "transform is not invertible");
        return TIFFCORE_ERROR_INVALID_ARG;
    }

    const int iSrcWidth = static_cast<int>(srcWidth);
    const int iSrcHeight = static_cast<int>(srcHeight);
    const bool bilinear = renderMode == TIFFCORE_RENDER_MODE_DISPLAY;

    for (int32_t y = clipTop; y < clipBottom; y++) {
        for (int32_t x = clipLeft; x < clipRight; x++) {
            float srcX;
            float srcY;
            inverse.apply(static_cast<float>(x) + 0.5f, static_cast<float>(y) + 0.5f, &srcX,
                    &srcY);
            if (srcX < 0 || srcY < 0 || srcX >= iSrcWidth || srcY >= iSrcHeight) {
                // Outside the source page: leave the destination pixel untouched (same contract
                // as PdfRenderer.Page#render).
                continue;
            }
            dstPixels[y * dstStridePixels + x] = bilinear
                    ? sampleBilinear(srcRaster, iSrcWidth, iSrcHeight, srcX, srcY)
                    : sampleNearest(srcRaster, iSrcWidth, iSrcHeight, srcX, srcY);
        }
    }

    return TIFFCORE_OK;
}
