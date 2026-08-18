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
