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

#ifndef TIFFRENDERER_TIFF_IO_H
#define TIFFRENDERER_TIFF_IO_H

#include <tiffio.h>

#include <cstdint>

namespace tiffrenderer {

// Opens a TIFF over a raw fd; size is int64_t, not long, to avoid truncating files over ~2GiB on 32-bit ABIs.
TIFF* openFromFd(int fd, int64_t size);

// Frees the bookkeeping struct stashed in the TIFF*'s client data; doesn't touch the fd.
void closeTiff(TIFF* tiff);

}  // namespace tiffrenderer

#endif  // TIFFRENDERER_TIFF_IO_H
