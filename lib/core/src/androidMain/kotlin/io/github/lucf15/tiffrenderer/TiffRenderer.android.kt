package io.github.lucf15.tiffrenderer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

public suspend fun TiffRenderer.Companion.open(context: Context, uri: Uri, options: TiffOptions = TiffOptions()): TiffRenderer =
    open(TiffSource.fromUri(context, uri), options)

public suspend fun TiffRenderer.Companion.open(file: File, options: TiffOptions = TiffOptions()): TiffRenderer =
    open(TiffSource.fromFile(file), options)

/** The caller retains ownership of pfd; the document uses a duplicate and independent read position. */
public suspend fun TiffRenderer.Companion.open(pfd: ParcelFileDescriptor, options: TiffOptions = TiffOptions()): TiffRenderer =
    open(TiffSource.borrowed(pfd), options)

public suspend fun TiffRenderer.Companion.renderBitmap(context: Context, uri: Uri, pageIndex: Int, maxWidth: Int, maxHeight: Int = Int.MAX_VALUE): Bitmap =
    render(TiffSource.fromUri(context, uri), pageIndex, RenderSize.fit(maxWidth, maxHeight)).image.asAndroidBitmap()
