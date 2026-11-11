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

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <limits>
#include <stdexcept>
#include <vector>
#ifndef _WIN32
#include <cerrno>
#include <unistd.h>
#endif

namespace tiffrenderer {
namespace {
constexpr uint64_t kMaxInput = 256ULL * 1024 * 1024;
constexpr uint64_t kMaxStaging = 512ULL * 1024 * 1024 - 32ULL * 1024 * 1024;

bool seekPosition(toff_t current, toff_t size, toff_t offset, int whence, toff_t* result) {
    if (whence == SEEK_SET) {
        if (offset > size) return false;
        *result = offset;
        return true;
    }
    const toff_t base = whence == SEEK_CUR ? current : size;
    if (whence != SEEK_CUR && whence != SEEK_END) return false;
    if (offset > static_cast<toff_t>(std::numeric_limits<int64_t>::max())) {
        const toff_t magnitude = 0 - offset;
        if (magnitude > base) return false;
        *result = base - magnitude;
    } else {
        if (offset > size - base) return false;
        *result = base + offset;
    }
    return true;
}

tsize_t writeProc(thandle_t, tdata_t, tsize_t) { return -1; }
int closeProc(thandle_t) { return 0; }
int mapProc(thandle_t, tdata_t*, toff_t*) { return 0; }
void unmapProc(thandle_t, tdata_t, toff_t) {}

#ifndef _WIN32
struct FdHandle { int fd; toff_t size; toff_t pos = 0; };
tsize_t readProc(thandle_t handle, tdata_t buffer, tsize_t requested) {
    auto* h = static_cast<FdHandle*>(handle);
    if (requested < 0) return -1;
    const auto count = static_cast<size_t>(std::min<toff_t>(requested, h->size - h->pos));
    ssize_t read;
    do { read = ::pread(h->fd, buffer, count, static_cast<off_t>(h->pos)); } while (read < 0 && errno == EINTR);
    if (read > 0) h->pos += static_cast<toff_t>(read);
    return read;
}
toff_t seekProc(thandle_t handle, toff_t offset, int whence) {
    auto* h = static_cast<FdHandle*>(handle);
    toff_t next;
    if (!seekPosition(h->pos, h->size, offset, whence, &next)) return static_cast<toff_t>(-1);
    h->pos = next;
    return next;
}
toff_t sizeProc(thandle_t handle) { return static_cast<FdHandle*>(handle)->size; }
#endif

struct MemHandle { std::vector<uint8_t> buffer; toff_t pos = 0; };
tsize_t memReadProc(thandle_t handle, tdata_t buffer, tsize_t requested) {
    auto* h = static_cast<MemHandle*>(handle);
    if (requested < 0) return -1;
    const auto count = static_cast<size_t>(std::min<toff_t>(requested, h->buffer.size() - h->pos));
    if (count) { std::memcpy(buffer, h->buffer.data() + h->pos, count); h->pos += count; }
    return static_cast<tsize_t>(count);
}
toff_t memSeekProc(thandle_t handle, toff_t offset, int whence) {
    auto* h = static_cast<MemHandle*>(handle);
    toff_t next;
    if (!seekPosition(h->pos, h->buffer.size(), offset, whence, &next)) return static_cast<toff_t>(-1);
    h->pos = next;
    return next;
}
toff_t memSizeProc(thandle_t handle) { return static_cast<MemHandle*>(handle)->buffer.size(); }
}

TIFF* openFromFd([[maybe_unused]] int fd, [[maybe_unused]] int64_t size) {
#ifdef _WIN32
    return nullptr;
#else
    if (fd < 0 || size < 0 || static_cast<uint64_t>(size) > kMaxInput) return nullptr;
    auto handle = std::make_unique<FdHandle>(FdHandle{fd, static_cast<toff_t>(size)});
    auto options = openOptions();
    TIFF* tiff = TIFFClientOpenExt("TiffRenderer", "r", handle.get(), readProc, writeProc,
        seekProc, closeProc, sizeProc, mapProc, unmapProc, options.get());
    if (tiff) handle.release();
    return tiff;
#endif
}

void closeTiff([[maybe_unused]] TIFF* tiff) {
#ifndef _WIN32
    if (!tiff) return;
    std::unique_ptr<FdHandle> handle(static_cast<FdHandle*>(TIFFClientdata(tiff)));
    TIFFClose(tiff);
#endif
}

TIFF* openFromMemory(const uint8_t* data, int64_t size) {
    if (size < 0 || static_cast<uint64_t>(size) > kMaxInput || static_cast<uint64_t>(size) > kMaxStaging / 2 || (!data && size)) return nullptr;
    auto handle = std::make_unique<MemHandle>();
    if (size) handle->buffer.assign(data, data + static_cast<size_t>(size));
    auto options = openOptions();
    TIFF* tiff = TIFFClientOpenExt("TiffRenderer", "r", handle.get(), memReadProc, writeProc,
        memSeekProc, closeProc, memSizeProc, mapProc, unmapProc, options.get());
    if (tiff) handle.release();
    return tiff;
}

void closeMemoryTiff(TIFF* tiff) {
    if (!tiff) return;
    std::unique_ptr<MemHandle> handle(static_cast<MemHandle*>(TIFFClientdata(tiff)));
    TIFFClose(tiff);
}
}
