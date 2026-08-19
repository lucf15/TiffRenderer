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

#include "tiff_io.h"

#include <cstdio>
#include <cstring>
#include <vector>

// Windows has no POSIX read()/lseek()/errno-style fd (and the JVM desktop JNI shim never calls
// these on that platform anyway, using tiffcore_open_path[_w] instead): stub them out there rather
// than pulling in unistd.h, so tiffrenderer_core still compiles for the Windows JVM native leg.
#ifndef _WIN32
#include <errno.h>
#include <unistd.h>
#endif

namespace tiffrenderer {

#ifdef _WIN32

TIFF* openFromFd(int /*fd*/, int64_t /*size*/) {
    return nullptr;
}

void closeTiff(TIFF* /*tiff*/) {}

#else

namespace {

struct FdHandle {
    int fd;
    toff_t size;
};

tsize_t readProc(thandle_t handle, tdata_t buf, tsize_t size) {
    const int fd = reinterpret_cast<FdHandle*>(handle)->fd;
    const ssize_t n = ::read(fd, buf, size);
    return n < 0 ? static_cast<tsize_t>(-1) : static_cast<tsize_t>(n);
}

tsize_t writeProc(thandle_t /*handle*/, tdata_t /*buf*/, tsize_t /*size*/) {
    // Read-only wrapper; TIFFClientOpen requires a non-null write proc even though it's never called.
    errno = EROFS;
    return static_cast<tsize_t>(-1);
}

toff_t seekProc(thandle_t handle, toff_t offset, int whence) {
    const int fd = reinterpret_cast<FdHandle*>(handle)->fd;
    const off_t result = ::lseek(fd, static_cast<off_t>(offset), whence);
    return result < 0 ? static_cast<toff_t>(-1) : static_cast<toff_t>(result);
}

int closeProc(thandle_t /*handle*/) {
    // No-op: closeTiff() frees FdHandle after TIFFClose() finishes; the fd stays owned by Java.
    return 0;
}

toff_t sizeProc(thandle_t handle) {
    return reinterpret_cast<FdHandle*>(handle)->size;
}

int mapFileProc(thandle_t /*handle*/, tdata_t* /*paddr*/, toff_t* /*psize*/) {
    // Forces libtiff's read/seek fallback: a ParcelFileDescriptor-backed fd isn't guaranteed mmap-able.
    return 0;
}

void unmapFileProc(thandle_t /*handle*/, tdata_t /*addr*/, toff_t /*size*/) {}

}  // namespace

TIFF* openFromFd(int fd, int64_t size) {
    auto* handle = new FdHandle{fd, static_cast<toff_t>(size)};
    TIFF* tiff = TIFFClientOpen("TiffRenderer", "r", reinterpret_cast<thandle_t>(handle),
            readProc, writeProc, seekProc, closeProc, sizeProc, mapFileProc, unmapFileProc);
    if (tiff == nullptr) {
        delete handle;
    }
    return tiff;
}

void closeTiff(TIFF* tiff) {
    auto* handle = reinterpret_cast<FdHandle*>(TIFFClientdata(tiff));
    TIFFClose(tiff);
    delete handle;
}

#endif  // _WIN32

namespace {

struct MemHandle {
    std::vector<uint8_t> buffer;
    toff_t pos = 0;
};

tsize_t memReadProc(thandle_t handle, tdata_t buf, tsize_t size) {
    auto* h = reinterpret_cast<MemHandle*>(handle);
    const toff_t remaining = h->buffer.size() > h->pos ? h->buffer.size() - h->pos : 0;
    const tsize_t n = static_cast<tsize_t>(static_cast<toff_t>(size) < remaining
            ? static_cast<toff_t>(size) : remaining);
    if (n > 0) {
        std::memcpy(buf, h->buffer.data() + h->pos, n);
        h->pos += n;
    }
    return n;
}

tsize_t memWriteProc(thandle_t /*handle*/, tdata_t /*buf*/, tsize_t /*size*/) {
    // Read-only wrapper; TIFFClientOpen requires a non-null write proc even though it's never called.
    return static_cast<tsize_t>(-1);
}

toff_t memSeekProc(thandle_t handle, toff_t offset, int whence) {
    auto* h = reinterpret_cast<MemHandle*>(handle);
    toff_t newPos;
    switch (whence) {
        case SEEK_SET: newPos = offset; break;
        case SEEK_CUR: newPos = h->pos + offset; break;
        case SEEK_END: newPos = static_cast<toff_t>(h->buffer.size()) + offset; break;
        default: return static_cast<toff_t>(-1);
    }
    h->pos = newPos;
    return newPos;
}

int memCloseProc(thandle_t /*handle*/) {
    // No-op: closeMemoryTiff() frees MemHandle after TIFFClose() finishes.
    return 0;
}

toff_t memSizeProc(thandle_t handle) {
    return static_cast<toff_t>(reinterpret_cast<MemHandle*>(handle)->buffer.size());
}

int memMapFileProc(thandle_t /*handle*/, tdata_t* /*paddr*/, toff_t* /*psize*/) {
    return 0;
}

void memUnmapFileProc(thandle_t /*handle*/, tdata_t /*addr*/, toff_t /*size*/) {}

}  // namespace

TIFF* openFromMemory(const uint8_t* data, int64_t size) {
    auto* handle = new MemHandle();
    handle->buffer.assign(data, data + size);
    TIFF* tiff = TIFFClientOpen("TiffRenderer", "r", reinterpret_cast<thandle_t>(handle),
            memReadProc, memWriteProc, memSeekProc, memCloseProc, memSizeProc, memMapFileProc,
            memUnmapFileProc);
    if (tiff == nullptr) {
        delete handle;
    }
    return tiff;
}

void closeMemoryTiff(TIFF* tiff) {
    auto* handle = reinterpret_cast<MemHandle*>(TIFFClientdata(tiff));
    TIFFClose(tiff);
    delete handle;
}

}  // namespace tiffrenderer
