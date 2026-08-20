package io.github.lucf15.tiffrenderer

/** An integer clip rect in destination-bitmap coordinates; mirrors `android.graphics.Rect`'s
 * corners without depending on it, so `commonMain` stays Android-free. */
public class TiffRect(public val left: Int, public val top: Int, public val right: Int, public val bottom: Int) {
    init {
        require(left < right && top < bottom) { "degenerate rect: ($left,$top,$right,$bottom)" }
    }

    public val width: Int get() = right - left
    public val height: Int get() = bottom - top
}
