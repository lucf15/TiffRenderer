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

#ifndef TIFFRENDERER_TIFF_CORE_H
#define TIFFRENDERER_TIFF_CORE_H

#include <stddef.h>
#include <stdint.h>

// Platform-neutral libtiff wrapper: no JNI, no Android types. Implemented in tiff_core.cpp and
// linked into the `tiffrenderer_core` CMake target so both the Android JNI shim
// (tiff_renderer_jni.cpp) and the iOS cinterop binding bind against the same logic.

#ifdef __cplusplus
extern "C" {
#endif

typedef struct TiffCoreDocument TiffCoreDocument;

typedef enum {
    TIFFCORE_OK = 0,
    TIFFCORE_ERROR_IO = 1,             // caller should surface as IOException
    TIFFCORE_ERROR_INVALID_ARG = 2,    // caller should surface as IllegalArgumentException
    TIFFCORE_ERROR_ILLEGAL_STATE = 3,  // caller should surface as IllegalStateException
} TiffCoreStatus;

typedef enum {
    TIFFCORE_RENDER_MODE_DISPLAY = 1,  // bilinear
    TIFFCORE_RENDER_MODE_PRINT = 2,    // nearest-neighbor
} TiffCoreRenderMode;

// Registers libtiff's process-global error/warning handlers. Call once per process before any
// other tiffcore_* function.
void tiffcore_global_init(void);

// Opens a TIFF over a raw, already-owned fd; size avoids a stat() round-trip and is int64_t to
// avoid truncating files over ~2GiB on 32-bit ABIs. On failure, *outDoc is left untouched and
// errBuf (if non-null) is filled with a human-readable message.
TiffCoreStatus tiffcore_open(int fd, int64_t size, TiffCoreDocument** outDoc, char* errBuf,
        size_t errBufLen);

// Opens a TIFF from a UTF-8 filesystem path via libtiff's portable stdio (fopen) backend, for
// platforms with no raw-fd concept (JVM desktop). Same failure contract as tiffcore_open.
TiffCoreStatus tiffcore_open_path(const char* utf8Path, TiffCoreDocument** outDoc, char* errBuf,
        size_t errBufLen);

#ifdef _WIN32
// Windows-only: opens from a UTF-16 (wchar_t) path via TIFFOpenW, since narrow fopen() on Windows
// doesn't reliably handle non-ASCII paths. JNI strings are natively UTF-16, so this avoids a lossy
// UTF-8 round-trip on this platform specifically.
TiffCoreStatus tiffcore_open_path_w(const wchar_t* utf16Path, TiffCoreDocument** outDoc,
        char* errBuf, size_t errBufLen);
#endif

// Opens a TIFF from an in-memory buffer (e.g. a classpath resource or an already-downloaded
// response body); data is copied internally, so the caller's buffer can be freed/reused
// immediately after this returns. Same failure contract as tiffcore_open.
TiffCoreStatus tiffcore_open_memory(const uint8_t* data, int64_t size, TiffCoreDocument** outDoc,
        char* errBuf, size_t errBufLen);

// Frees doc and its libtiff handle; a no-op if doc is null. Does not touch the fd.
void tiffcore_close(TiffCoreDocument* doc);

int32_t tiffcore_get_page_count(TiffCoreDocument* doc);

// Seeks to pageIndex and reports its dimensions without decoding pixels.
TiffCoreStatus tiffcore_open_page(TiffCoreDocument* doc, int32_t pageIndex, uint32_t* outWidth,
        uint32_t* outHeight, char* errBuf, size_t errBufLen);

// Decodes pageIndex now and caches it on doc so a subsequent tiffcore_render_page for the same
// page reuses it instead of redecoding. Overwrites whatever was previously cached.
TiffCoreStatus tiffcore_retain_raster(TiffCoreDocument* doc, int32_t pageIndex, char* errBuf,
        size_t errBufLen);

// Frees whatever tiffcore_retain_raster cached, if anything; safe to call unconditionally.
void tiffcore_release_raster(TiffCoreDocument* doc);

// Renders pageIndex into a caller-owned packed RGBA8888 buffer (dstPixels), reusing the
// retainRaster() cache if it matches pageIndex, decoding fresh otherwise. dstStridePixels,
// dstWidth, dstHeight describe dstPixels so clip bounds are re-validated here too. matrix is 6
// affine coefficients (android.graphics.Matrix#getValues() order) mapping destination pixels to
// source pixels. Pixels outside the clip or that map outside the source page are left untouched.
TiffCoreStatus tiffcore_render_page(TiffCoreDocument* doc, int32_t pageIndex, uint32_t* dstPixels,
        int32_t dstStridePixels, int32_t dstWidth, int32_t dstHeight, int32_t clipLeft,
        int32_t clipTop, int32_t clipRight, int32_t clipBottom, const float matrix[6],
        TiffCoreRenderMode renderMode, char* errBuf, size_t errBufLen);

#ifdef __cplusplus
}
#endif

#endif  // TIFFRENDERER_TIFF_CORE_H
