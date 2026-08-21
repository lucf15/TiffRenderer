package io.github.lucf15.tiffrenderer

/** A 2D affine transform mapping page pixels to destination-bitmap pixels, in
 * `android.graphics.Matrix#getValues()` order: `[mxx, mxy, mtx, myx, myy, mty]` — the
 * representation `tiff_core`'s native render call expects. */
public class TiffTransform(values: FloatArray) {
    init {
        require(values.size == 6) { "values must have 6 elements (mxx,mxy,mtx,myx,myy,mty), got ${values.size}" }
    }

    private val backingValues: FloatArray = values.copyOf()

    /** A copy, not the live backing array: mutating the result can't corrupt this transform for
     * any other holder reusing it across calls. */
    public val values: FloatArray get() = backingValues.copyOf()
}
