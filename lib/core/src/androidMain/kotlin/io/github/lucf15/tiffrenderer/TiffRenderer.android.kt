package io.github.lucf15.tiffrenderer

import android.os.ParcelFileDescriptor

/** Opens [pfd] directly instead of requiring `TiffSource.fromParcelFileDescriptor(pfd)`. */
public fun TiffRenderer(pfd: ParcelFileDescriptor): TiffRenderer = TiffRenderer(TiffSource.fromParcelFileDescriptor(pfd))
