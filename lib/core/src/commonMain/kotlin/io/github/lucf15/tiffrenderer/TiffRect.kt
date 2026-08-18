package io.github.lucf15.tiffrenderer

/** An integer clip rect in destination-bitmap coordinates; mirrors `android.graphics.Rect`'s
 * corners without depending on it, so `commonMain` stays Android-free. */
class TiffRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(left < right && top < bottom) { "degenerate rect: ($left,$top,$right,$bottom)" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
