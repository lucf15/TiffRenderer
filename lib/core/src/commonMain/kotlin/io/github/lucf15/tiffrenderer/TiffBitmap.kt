package io.github.lucf15.tiffrenderer

/** A destination pixel buffer for [TiffPage.render]: packed RGBA8888, no Skia/UI-bitmap
 * dependency. Android's `actual` wraps an existing `ARGB_8888` `android.graphics.Bitmap`. */
expect class TiffBitmap {
    val width: Int
    val height: Int
}

/** Allocates a fresh, blank [TiffBitmap] as a top-level factory, since each `actual`'s
 * constructor shape differs. */
expect fun createTiffBitmap(width: Int, height: Int): TiffBitmap

/** A packed `0xAARRGGBB` int, [android.graphics.Color]'s convention: each `actual` reorders its
 * own native pixel layout to match, so cross-platform test assertions don't need to know which
 * platform they're running on. */
internal expect fun TiffBitmap.pixelAt(x: Int, y: Int): Int
