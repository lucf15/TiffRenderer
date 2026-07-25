package io.github.lucf15.tiffrenderer.sample.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.IOException

fun openTiffFile(
    context: Context,
    uri: Uri,
): ParcelFileDescriptor =
    context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IOException("cannot open $uri")
