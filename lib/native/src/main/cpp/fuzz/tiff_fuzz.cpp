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

#include <cstddef>
#include <cstdint>
#include <limits>
#include <vector>

#include "../tiff_core.h"

namespace {

// Trailing control bytes for render() params, so the document itself still starts at byte 0.
constexpr size_t kControlBytes = 11;

// Drives every tiffcore_* entry point a real TiffRenderer session exercises, over hostile input.
// tiffcore_open_memory copies its input internally, so libFuzzer's own input buffer never has to
// outlive this call.
void fuzzOneDocument(const uint8_t* data, size_t size) {
    if (size < kControlBytes) {
        return;
    }
    const size_t docSize = size - kControlBytes;
    const uint8_t* control = data + docSize;

    char errBuf[512];

    TiffCoreDocument* doc = nullptr;
    if (tiffcore_open_memory(data, static_cast<int64_t>(docSize), &doc, errBuf, sizeof(errBuf)) != TIFFCORE_OK) {
        return;
    }

    // Capped so one crafted document with a huge page count can't dominate the fuzzer's time
    // budget; the interesting per-page decode paths are already reached well within this many.
    const int32_t pageCount = tiffcore_get_page_count(doc);
    const int32_t pagesToTry = pageCount > 8 ? 8 : pageCount;

    constexpr int32_t kDstSize = 64;
    // Occasionally wider than kDstSize, to exercise the dstStridePixels != dstWidth path a real
    // Android AndroidBitmap row stride can hit but every current in-process caller (JVM, iOS) never
    // does.
    const int32_t dstStride = kDstSize + static_cast<int32_t>(control[10] % 16);
    std::vector<uint32_t> dst(static_cast<size_t>(dstStride) * kDstSize, 0);
    // Half the time, retain the raster before rendering so tiffcore_render_page's
    // doc->cachedPageIndex == pageIndex branch (reusing the cache instead of redecoding) is
    // actually exercised, not just its always-miss default.
    const bool retainBeforeRender = (control[9] & 1) != 0;

    const TiffCoreRenderMode renderMode =
            (control[0] & 1) ? TIFFCORE_RENDER_MODE_DISPLAY : TIFFCORE_RENDER_MODE_PRINT;
    // 0.05..2.05: covers both minification (drives the mip pyramid) and magnification.
    float scaleX = 0.05f + (static_cast<float>(control[1]) / 255.0f) * 2.0f;
    float scaleY = 0.05f + (static_cast<float>(control[2]) / 255.0f) * 2.0f;
    float skewX = -2.05f + (static_cast<float>(control[7]) / 255.0f) * 4.10f;
    float skewY = -2.05f + (static_cast<float>(control[8]) / 255.0f) * 4.10f;
    // Reachable, not just theoretically possible: forces AffineTransform::invert()'s failure path.
    switch ((control[0] >> 1) & 0x3) {
        case 1: scaleX = std::numeric_limits<float>::quiet_NaN(); break;
        case 2: scaleY = std::numeric_limits<float>::infinity(); break;
        case 3: skewX = scaleX; skewY = scaleY; break;
        default: break;
    }
    const float matrix[6] = {scaleX, skewX, 0.0f, skewY, scaleY, 0.0f};

    const int32_t clipLeft = static_cast<int32_t>(control[3]) % kDstSize;
    const int32_t clipTop = static_cast<int32_t>(control[4]) % kDstSize;
    const int32_t clipRight = clipLeft + 1 + static_cast<int32_t>(control[5]) % (kDstSize - clipLeft);
    const int32_t clipBottom = clipTop + 1 + static_cast<int32_t>(control[6]) % (kDstSize - clipTop);

    for (int32_t pageIndex = 0; pageIndex < pagesToTry; pageIndex++) {
        uint32_t width = 0;
        uint32_t height = 0;
        if (tiffcore_open_page(doc, pageIndex, &width, &height, errBuf, sizeof(errBuf)) != TIFFCORE_OK) {
            continue;
        }

        if (retainBeforeRender) {
            tiffcore_retain_raster(doc, pageIndex, errBuf, sizeof(errBuf));
        }

        tiffcore_render_page(doc, pageIndex, dst.data(), dstStride, kDstSize, kDstSize, clipLeft, clipTop,
                clipRight, clipBottom, matrix, renderMode, errBuf, sizeof(errBuf));

        if (!retainBeforeRender) {
            tiffcore_retain_raster(doc, pageIndex, errBuf, sizeof(errBuf));
        }
        // release_raster is documented safe to call unconditionally, matching TiffPage#close().
        tiffcore_release_raster(doc);
    }

    tiffcore_close(doc);
}

}  // namespace

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    // Function-local static: C++11 guarantees this initializes exactly once, thread-safely, on
    // first call, since libFuzzer may run this from more than one worker.
    static const bool globalInitDone = [] {
        tiffcore_global_init();
        return true;
    }();
    (void)globalInitDone;

    fuzzOneDocument(data, size);
    return 0;
}
