package io.github.lucf15.tiffrenderer

/** A 2D affine transform mapping page pixels to destination-bitmap pixels, in
 * `android.graphics.Matrix#getValues()` order: `[mxx, mxy, mtx, myx, myy, mty]` — the
 * representation `tiff_core`'s native render call expects. */
public class TiffTransform(values: FloatArray) {
    init {
        require(values.all { it.isFinite() }) { "transform must be finite" }
        require(values.size == 6) { "values must have 6 elements (mxx,mxy,mtx,myx,myy,mty), got ${values.size}" }
    }

    private val backingValues: FloatArray = values.copyOf()

    /** A copy, not the live backing array: mutating the result can't corrupt this transform for
     * any other holder reusing it across calls. */
    public val values: FloatArray get() = backingValues.copyOf()
    public companion object {
        public fun identity(): TiffTransform = scale(1f)
        public fun scale(x: Float, y: Float = x): TiffTransform = TiffTransform(floatArrayOf(x, 0f, 0f, 0f, y, 0f))
        public fun translate(x: Float, y: Float): TiffTransform = TiffTransform(floatArrayOf(1f, 0f, x, 0f, 1f, y))
        public fun rotate(degrees: Float): TiffTransform {
            val radians = degrees.toDouble() * kotlin.math.PI / 180
            val c = kotlin.math.cos(radians).toFloat()
            val s = kotlin.math.sin(radians).toFloat()
            return TiffTransform(floatArrayOf(c, -s, 0f, s, c, 0f))
        }
    }
}
