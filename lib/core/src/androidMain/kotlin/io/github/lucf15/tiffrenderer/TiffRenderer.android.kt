package io.github.lucf15.tiffrenderer

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineDispatcher

/** Opens [pfd] directly instead of requiring `TiffSource.fromParcelFileDescriptor(pfd)`. */
public suspend fun TiffRenderer(
    pfd: ParcelFileDescriptor,
    dispatcher: CoroutineDispatcher = defaultTiffDispatcher,
): TiffRenderer = TiffRenderer.open(TiffSource.fromParcelFileDescriptor(pfd), dispatcher)
