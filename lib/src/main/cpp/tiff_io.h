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

namespace tiffrenderer {

// Opens a TIFF over a raw file descriptor via TIFFClientOpen, the same "custom I/O callbacks
// instead of a path" trick android.graphics.pdf.PdfRenderer uses for pdfium's
// FPDF_LoadCustomDocument (see PdfUtils.cpp's getBlock/pread — this is the libtiff analogue).
// The fd itself is never opened or closed here: the caller (TiffRenderer.java, via
// ParcelFileDescriptor) owns its lifecycle for the whole native document's lifetime, exactly
// like PdfRenderer leaves fd ownership to the Java side.
TIFF* openFromFd(int fd, long size);

// Releases the resources associated with a TIFF* returned by openFromFd, including the small
// bookkeeping struct stashed in its client data.
void closeTiff(TIFF* tiff);

}  // namespace tiffrenderer

#endif  // TIFFRENDERER_TIFF_IO_H
