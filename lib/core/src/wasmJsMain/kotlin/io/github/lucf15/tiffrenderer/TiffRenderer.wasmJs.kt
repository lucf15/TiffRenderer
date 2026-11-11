package io.github.lucf15.tiffrenderer

import org.w3c.files.Blob

public suspend fun TiffRenderer.Companion.open(file: Blob, options: TiffOptions = TiffOptions()): TiffRenderer = open(TiffSource.fromBlob(file), options)
