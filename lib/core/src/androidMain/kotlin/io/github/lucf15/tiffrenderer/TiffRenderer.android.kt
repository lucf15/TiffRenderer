package io.github.lucf15.tiffrenderer

import android.os.ParcelFileDescriptor

/** Android-native convenience: opens [pfd] directly instead of requiring
 * `TiffRenderer(TiffSource.fromParcelFileDescriptor(pfd))`; mirrors `PdfRenderer`'s own
 * `ParcelFileDescriptor` constructor. */
fun TiffRenderer(pfd: ParcelFileDescriptor): TiffRenderer = TiffRenderer(TiffSource.fromParcelFileDescriptor(pfd))
