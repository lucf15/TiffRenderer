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

#include <errno.h>
#include <unistd.h>

namespace tiffrenderer {

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
    // This wrapper only ever opens TIFFs "r" (read-only) — TIFFClientOpen still requires a
    // non-null write proc to be registered, it just never gets called in read mode.
    errno = EROFS;
    return static_cast<tsize_t>(-1);
}

toff_t seekProc(thandle_t handle, toff_t offset, int whence) {
    const int fd = reinterpret_cast<FdHandle*>(handle)->fd;
    const off_t result = ::lseek(fd, static_cast<off_t>(offset), whence);
    return result < 0 ? static_cast<toff_t>(-1) : static_cast<toff_t>(result);
}

int closeProc(thandle_t /*handle*/) {
    // Deliberately does not touch the fd or free FdHandle here (TIFFClose() may invoke this
    // more than once across error paths). closeTiff() below is the single place that frees
    // FdHandle, after TIFFClose() has fully finished with it. The fd itself is never closed
    // natively at all — Java's ParcelFileDescriptor owns that, matching how PdfRenderer leaves
    // fd lifecycle entirely to the Java side.
    return 0;
}

toff_t sizeProc(thandle_t handle) {
    return reinterpret_cast<FdHandle*>(handle)->size;
}

int mapFileProc(thandle_t /*handle*/, tdata_t* /*paddr*/, toff_t* /*psize*/) {
    // Returning failure here forces libtiff to fall back to read/seek instead of mmap: a
    // ParcelFileDescriptor-backed fd (content:// stream, pipe, etc.) isn't guaranteed to be
    // mmap-able the way a plain local file path would be.
    return 0;
}

void unmapFileProc(thandle_t /*handle*/, tdata_t /*addr*/, toff_t /*size*/) {}

}  // namespace

TIFF* openFromFd(int fd, long size) {
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

}  // namespace tiffrenderer
