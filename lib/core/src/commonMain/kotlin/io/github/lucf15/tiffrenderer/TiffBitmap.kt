package io.github.lucf15.tiffrenderer

/** A destination pixel buffer for [TiffPage.render]: packed RGBA8888, no Skia/UI-bitmap
 * dependency. Android's `actual` wraps an existing `ARGB_8888` `android.graphics.Bitmap`. */
public expect class TiffBitmap {
    public val width: Int
    public val height: Int
}

/** Allocates a fresh, blank [TiffBitmap] as a top-level factory, since each `actual`'s
 * constructor shape differs. */
public expect fun createTiffBitmap(width: Int, height: Int): TiffBitmap

/** A packed `0xAARRGGBB` int, [android.graphics.Color]'s convention: each `actual` reorders its
 * own native pixel layout to match, so cross-platform test assertions don't need to know which
 * platform they're running on. */
internal expect fun TiffBitmap.pixelAt(x: Int, y: Int): Int

/** Shared by the iOS/JVM `actual`s, which allocate their own pixel buffer sized `width * height`;
 * Android's doesn't need this since it wraps an existing, already-allocated `Bitmap`. */
internal fun requirePositiveNonOverflowingBitmapDimensions(width: Int, height: Int) {
    require(width > 0 && height > 0) { "width/height must be positive, got ${width}x$height" }
    require(width.toLong() * height.toLong() * 4 <= Int.MAX_VALUE) {
        "width * height overflows Int, got ${width}x$height"
    }
}
