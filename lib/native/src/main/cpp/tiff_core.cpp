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

#include <algorithm>
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
// is expected to be externally serialized per document by the caller (each TiffCoreHandle owns
// its own lock), so one thread_local scratch buffer suffices.
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

// Allocation-free by construction: called from catch blocks reacting to std::bad_alloc, so it
// must never itself risk throwing.
void fillErrBuf(char* errBuf, size_t errBufLen, const char* fallbackMessage) noexcept {
    if (errBuf == nullptr || errBufLen == 0) {
        return;
    }
    const char* message = gLastError.empty() ? fallbackMessage : gLastError.c_str();
    std::snprintf(errBuf, errBufLen, "%s", message);
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

// Halves a raster via 2x2 premultiplied box averaging (odd dimensions round up, duplicating the
// last row/column); one mip level of the pyramid tiffcore_render_page builds for minification.
std::vector<uint32_t> halveRaster(const uint32_t* src, int srcWidth, int srcHeight, int* outWidth,
        int* outHeight) {
    const int dstWidth = (srcWidth + 1) / 2;
    const int dstHeight = (srcHeight + 1) / 2;
    std::vector<uint32_t> dst(static_cast<size_t>(dstWidth) * static_cast<size_t>(dstHeight));

    for (int dy = 0; dy < dstHeight; dy++) {
        const int sy0 = dy * 2;
        const int sy1 = sy0 + 1 < srcHeight ? sy0 + 1 : sy0;
        for (int dx = 0; dx < dstWidth; dx++) {
            const int sx0 = dx * 2;
            const int sx1 = sx0 + 1 < srcWidth ? sx0 + 1 : sx0;

            float r = 0.0f, g = 0.0f, b = 0.0f, a = 0.0f;
            auto accumulate = [&](int px, int py) {
                const uint32_t p = src[static_cast<size_t>(py) * srcWidth + px];
                const float pa = static_cast<float>((p >> 24) & 0xff);
                const float scale = pa / 255.0f;
                r += static_cast<float>(p & 0xff) * scale;
                g += static_cast<float>((p >> 8) & 0xff) * scale;
                b += static_cast<float>((p >> 16) & 0xff) * scale;
                a += pa;
            };
            accumulate(sx0, sy0);
            accumulate(sx1, sy0);
            accumulate(sx0, sy1);
            accumulate(sx1, sy1);

            r *= 0.25f;
            g *= 0.25f;
            b *= 0.25f;
            a *= 0.25f;
            if (a > 0.0f) {
                const float invA = 255.0f / a;
                r *= invA;
                g *= invA;
                b *= invA;
            }
            auto toByte = [](float v) { return static_cast<uint32_t>(clampFloat(v + 0.5f, 0.0f, 255.0f)); };
            dst[static_cast<size_t>(dy) * static_cast<size_t>(dstWidth) + dx] =
                    toByte(r) | (toByte(g) << 8) | (toByte(b) << 16) | (toByte(a) << 24);
        }
    }
    *outWidth = dstWidth;
    *outHeight = dstHeight;
    return dst;
}

}  // namespace

// Picks the matching close function for whatever TIFFClientOpen client-data type tiff was opened
// with: FdHandle* (kFd), MemHandle* (kMemory), or libtiff's own stdio state (kPath).
enum class TiffIoKind { kPath, kFd, kMemory };

// Holds the TIFF* plus an optional single-page raster cache for tiffcore_retain_raster().
struct TiffCoreDocument {
    TiffCoreDocument(TIFF* t, TiffIoKind ioKind) : tiff(t), ioKind(ioKind) {}

    TIFF* tiff;
    TiffIoKind ioKind;
    int32_t cachedPageIndex = -1;
    uint32_t cachedWidth = 0;
    uint32_t cachedHeight = 0;
    bool cachedPartial = false;
    std::vector<uint32_t> cachedRaster;
};

namespace {

// Seeks to pageIndex and decodes it to a packed-RGBA raster; caller must gLastError.clear() first.
// *outPartial is set true if libtiff tolerated a decode error partway through (stopOnError=0 below
// means TIFFReadRGBAImageOriented can still report success after one), false otherwise.
bool decodePage(TIFF* tiff, int32_t pageIndex, uint32_t* outWidth, uint32_t* outHeight,
        std::vector<uint32_t>* outRaster, bool* outPartial, char* errBuf, size_t errBufLen) {
    // A negative pageIndex would otherwise wrap to a huge tdir_t (unsigned); TIFFSetDirectory
    // would likely still reject it, but this is a public C API and the wrap itself is worth
    // never relying on.
    if (pageIndex < 0) {
        fillErrBuf(errBuf, errBufLen, "page index cannot be negative");
        return false;
    }
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
    // overcommit and OOM-killing later instead of failing cleanly. Scaled by pointer width: a
    // 32-bit process's entire address space is ~4GB (often much less usable in practice), so the
    // 64-bit cap would leave far too little headroom for everything else the process needs.
#if UINTPTR_MAX == 0xFFFFFFFF
    constexpr uint64_t kMaxDecodedPixels = 64'000'000;  // ~256MB packed RGBA
#else
    constexpr uint64_t kMaxDecodedPixels = 250'000'000;  // ~1GB packed RGBA
#endif
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
    *outPartial = !gLastError.empty();
    return true;
}

}  // namespace

void tiffcore_global_init(void) {
    TIFFSetErrorHandler(errorHandler);
    TIFFSetWarningHandler(warningHandler);
}

namespace {

void closeByKind(TIFF* tiff, TiffIoKind ioKind) {
    switch (ioKind) {
        case TiffIoKind::kFd:
            tiffrenderer::closeTiff(tiff);
            break;
        case TiffIoKind::kMemory:
            tiffrenderer::closeMemoryTiff(tiff);
            break;
        case TiffIoKind::kPath:
            TIFFClose(tiff);
            break;
    }
}

TiffCoreStatus wrapOpenedTiff(TIFF* tiff, TiffIoKind ioKind, TiffCoreDocument** outDoc,
        char* errBuf, size_t errBufLen) {
    if (tiff == nullptr) {
        fillErrBuf(errBuf, errBufLen, "cannot open TIFF");
        return TIFFCORE_ERROR_IO;
    }
    try {
        *outDoc = new TiffCoreDocument(tiff, ioKind);
        return TIFFCORE_OK;
    } catch (const std::exception&) {
        closeByKind(tiff, ioKind);
        fillErrBuf(errBuf, errBufLen, "out of memory opening TIFF");
        return TIFFCORE_ERROR_IO;
    }
}

}  // namespace

TiffCoreStatus tiffcore_open(int fd, int64_t size, TiffCoreDocument** outDoc, char* errBuf,
        size_t errBufLen) {
    if (outDoc == nullptr) {
        fillErrBuf(errBuf, errBufLen, "outDoc cannot be null");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    if (size < 0) {
        fillErrBuf(errBuf, errBufLen, "size cannot be negative");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    gLastError.clear();
    try {
        TIFF* tiff = tiffrenderer::openFromFd(fd, size);
        return wrapOpenedTiff(tiff, TiffIoKind::kFd, outDoc, errBuf, errBufLen);
    } catch (...) {
        fillErrBuf(errBuf, errBufLen, "out of memory opening TIFF");
        return TIFFCORE_ERROR_IO;
    }
}

TiffCoreStatus tiffcore_open_path(const char* utf8Path, TiffCoreDocument** outDoc, char* errBuf,
        size_t errBufLen) {
    if (outDoc == nullptr) {
        fillErrBuf(errBuf, errBufLen, "outDoc cannot be null");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    if (utf8Path == nullptr) {
        fillErrBuf(errBuf, errBufLen, "path cannot be null");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    gLastError.clear();
    try {
        TIFF* tiff = TIFFOpen(utf8Path, "r");
        return wrapOpenedTiff(tiff, TiffIoKind::kPath, outDoc, errBuf, errBufLen);
    } catch (...) {
        fillErrBuf(errBuf, errBufLen, "out of memory opening TIFF");
        return TIFFCORE_ERROR_IO;
    }
}

#ifdef _WIN32
TiffCoreStatus tiffcore_open_path_w(const wchar_t* utf16Path, TiffCoreDocument** outDoc,
        char* errBuf, size_t errBufLen) {
    if (outDoc == nullptr) {
        fillErrBuf(errBuf, errBufLen, "outDoc cannot be null");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    if (utf16Path == nullptr) {
        fillErrBuf(errBuf, errBufLen, "path cannot be null");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    gLastError.clear();
    try {
        TIFF* tiff = TIFFOpenW(utf16Path, "r");
        return wrapOpenedTiff(tiff, TiffIoKind::kPath, outDoc, errBuf, errBufLen);
    } catch (...) {
        fillErrBuf(errBuf, errBufLen, "out of memory opening TIFF");
        return TIFFCORE_ERROR_IO;
    }
}
#endif

TiffCoreStatus tiffcore_open_memory(const uint8_t* data, int64_t size, TiffCoreDocument** outDoc,
        char* errBuf, size_t errBufLen) {
    if (outDoc == nullptr) {
        fillErrBuf(errBuf, errBufLen, "outDoc cannot be null");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    if (size < 0) {
        fillErrBuf(errBuf, errBufLen, "size cannot be negative");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    if (data == nullptr && size > 0) {
        fillErrBuf(errBuf, errBufLen, "data cannot be null when size is positive");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    gLastError.clear();
    try {
        TIFF* tiff = tiffrenderer::openFromMemory(data, size);
        return wrapOpenedTiff(tiff, TiffIoKind::kMemory, outDoc, errBuf, errBufLen);
    } catch (...) {
        fillErrBuf(errBuf, errBufLen, "out of memory opening TIFF");
        return TIFFCORE_ERROR_IO;
    }
}

void tiffcore_close(TiffCoreDocument* doc) {
    if (doc == nullptr) {
        return;
    }
    try {
        closeByKind(doc->tiff, doc->ioKind);
        delete doc;
    } catch (...) {
        TIFFCORE_LOGE("tiffcore_close: unexpected exception, leaking the document handle");
    }
}

int32_t tiffcore_get_page_count(TiffCoreDocument* doc) {
    if (doc == nullptr) {
        return 0;
    }
    try {
        return static_cast<int32_t>(TIFFNumberOfDirectories(doc->tiff));
    } catch (...) {
        return 0;
    }
}

TiffCoreStatus tiffcore_open_page(TiffCoreDocument* doc, int32_t pageIndex, uint32_t* outWidth,
        uint32_t* outHeight, char* errBuf, size_t errBufLen) {
    if (doc == nullptr) {
        fillErrBuf(errBuf, errBufLen, "TIFF document is not open");
        return TIFFCORE_ERROR_ILLEGAL_STATE;
    }
    if (outWidth == nullptr || outHeight == nullptr) {
        fillErrBuf(errBuf, errBufLen, "outWidth/outHeight cannot be null");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    gLastError.clear();
    try {
        if (pageIndex < 0) {
            fillErrBuf(errBuf, errBufLen, "page index cannot be negative");
            return TIFFCORE_ERROR_INVALID_ARG;
        }
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
    } catch (...) {
        fillErrBuf(errBuf, errBufLen, "unexpected internal error opening TIFF page");
        return TIFFCORE_ERROR_IO;
    }
}

TiffCoreStatus tiffcore_retain_raster(TiffCoreDocument* doc, int32_t pageIndex, char* errBuf,
        size_t errBufLen) {
    if (doc == nullptr) {
        fillErrBuf(errBuf, errBufLen, "TIFF document is not open");
        return TIFFCORE_ERROR_ILLEGAL_STATE;
    }
    gLastError.clear();
    try {
        uint32_t width;
        uint32_t height;
        std::vector<uint32_t> raster;
        bool partial = false;
        if (!decodePage(doc->tiff, pageIndex, &width, &height, &raster, &partial, errBuf,
                    errBufLen)) {
            return TIFFCORE_ERROR_IO;
        }
        doc->cachedRaster = std::move(raster);
        doc->cachedWidth = width;
        doc->cachedHeight = height;
        doc->cachedPartial = partial;
        doc->cachedPageIndex = pageIndex;
        return partial ? TIFFCORE_OK_PARTIAL : TIFFCORE_OK;
    } catch (...) {
        fillErrBuf(errBuf, errBufLen, "unexpected internal error decoding TIFF page");
        return TIFFCORE_ERROR_IO;
    }
}

void tiffcore_release_raster(TiffCoreDocument* doc) {
    if (doc == nullptr) {
        return;
    }
    try {
        doc->cachedPageIndex = -1;
        doc->cachedWidth = 0;
        doc->cachedHeight = 0;
        doc->cachedPartial = false;
        // release the backing memory, not just clear()
        std::vector<uint32_t>().swap(doc->cachedRaster);
    } catch (...) {
        TIFFCORE_LOGE("tiffcore_release_raster: unexpected exception");
    }
}

TiffCoreStatus tiffcore_render_page(TiffCoreDocument* doc, int32_t pageIndex, uint32_t* dstPixels,
        int32_t dstStridePixels, int32_t dstWidth, int32_t dstHeight, int32_t clipLeft,
        int32_t clipTop, int32_t clipRight, int32_t clipBottom, const float matrix[6],
        TiffCoreRenderMode renderMode, char* errBuf, size_t errBufLen) {
    // Cleared before any fillErrBuf call below, including the early-return validations: fillErrBuf
    // prefers a non-empty gLastError over its own fallback message, so a stale message left behind
    // by an unrelated earlier call would otherwise mask these fallbacks entirely.
    gLastError.clear();
    if (doc == nullptr) {
        fillErrBuf(errBuf, errBufLen, "TIFF document is not open");
        return TIFFCORE_ERROR_ILLEGAL_STATE;
    }
    if (dstPixels == nullptr) {
        fillErrBuf(errBuf, errBufLen, "destination cannot be null");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    if (pageIndex < 0) {
        fillErrBuf(errBuf, errBufLen, "page index cannot be negative");
        return TIFFCORE_ERROR_INVALID_ARG;
    }
    if (dstStridePixels < dstWidth) {
        fillErrBuf(errBuf, errBufLen, "dstStridePixels cannot be smaller than dstWidth");
        return TIFFCORE_ERROR_INVALID_ARG;
    }

    try {
        // Never trust caller-supplied clip bounds; check before paying for a decode.
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

        // Reuse the retainRaster() cache instead of redecoding if it matches this page.
        uint32_t srcWidth;
        uint32_t srcHeight;
        const uint32_t* srcRaster;
        std::vector<uint32_t> decoded;
        bool partial = false;
        if (doc->cachedPageIndex == pageIndex) {
            srcWidth = doc->cachedWidth;
            srcHeight = doc->cachedHeight;
            srcRaster = doc->cachedRaster.data();
            partial = doc->cachedPartial;
        } else {
            if (!decodePage(doc->tiff, pageIndex, &srcWidth, &srcHeight, &decoded, &partial, errBuf,
                        errBufLen)) {
                return TIFFCORE_ERROR_IO;
            }
            srcRaster = decoded.data();
        }

        const int iSrcWidth = static_cast<int>(srcWidth);
        const int iSrcHeight = static_cast<int>(srcHeight);
        const bool bilinear = renderMode == TIFFCORE_RENDER_MODE_DISPLAY;

        // Minification (scale < 1 on either axis) needs a pre-downsampled mip level, not just a
        // smaller sample window into the full-res raster: sampleBilinear/sampleNearest only ever
        // look at 4 (or 1) source pixels around one point, regardless of how many source pixels an
        // output pixel actually covers, so without this a large scan shrunk into a small view
        // aliases badly.
        const float scaleX = std::sqrt(matrix[0] * matrix[0] + matrix[3] * matrix[3]);
        const float scaleY = std::sqrt(matrix[1] * matrix[1] + matrix[4] * matrix[4]);
        const float minScale = std::min(scaleX, scaleY);
        int mipLevels = 0;
        if (minScale > 0.0f && minScale < 1.0f) {
            mipLevels = static_cast<int>(std::floor(std::log2(1.0f / minScale)));
        }
        // Two alternating buffers rather than one reassigned in place: halveRaster's own inputs
        // (sampleRaster, pointing into whichever buffer produced the previous level) must stay
        // valid for the full duration of the call that produces the next level, not just until
        // some reassignment happens to run after the call returns.
        std::vector<uint32_t> mipBufferA;
        std::vector<uint32_t> mipBufferB;
        bool useBufferA = true;
        const uint32_t* sampleRaster = srcRaster;
        int sampleWidth = iSrcWidth;
        int sampleHeight = iSrcHeight;
        float mipScale = 1.0f;
        for (int level = 0; level < mipLevels && (sampleWidth > 1 || sampleHeight > 1); level++) {
            int nextWidth;
            int nextHeight;
            std::vector<uint32_t>& mipBuffer = useBufferA ? mipBufferA : mipBufferB;
            mipBuffer = halveRaster(sampleRaster, sampleWidth, sampleHeight, &nextWidth,
                    &nextHeight);
            sampleRaster = mipBuffer.data();
            useBufferA = !useBufferA;
            sampleWidth = nextWidth;
            sampleHeight = nextHeight;
            mipScale *= 2.0f;
        }

        for (int32_t y = clipTop; y < clipBottom; y++) {
            for (int32_t x = clipLeft; x < clipRight; x++) {
                float srcX;
                float srcY;
                inverse.apply(static_cast<float>(x) + 0.5f, static_cast<float>(y) + 0.5f, &srcX,
                        &srcY);
                if (srcX < 0 || srcY < 0 || srcX >= iSrcWidth || srcY >= iSrcHeight) {
                    // Outside the source page: leave the destination pixel untouched (same
                    // contract as PdfRenderer.Page#render).
                    continue;
                }
                const float sampleX = srcX / mipScale;
                const float sampleY = srcY / mipScale;
                dstPixels[static_cast<size_t>(y) * static_cast<size_t>(dstStridePixels)
                        + static_cast<size_t>(x)] = bilinear
                        ? sampleBilinear(sampleRaster, sampleWidth, sampleHeight, sampleX, sampleY)
                        : sampleNearest(sampleRaster, sampleWidth, sampleHeight, sampleX, sampleY);
            }
        }

        return partial ? TIFFCORE_OK_PARTIAL : TIFFCORE_OK;
    } catch (...) {
        fillErrBuf(errBuf, errBufLen, "unexpected internal error rendering TIFF page");
        return TIFFCORE_ERROR_IO;
    }
}
