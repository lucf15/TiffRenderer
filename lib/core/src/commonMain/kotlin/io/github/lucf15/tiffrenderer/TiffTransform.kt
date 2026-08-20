package io.github.lucf15.tiffrenderer

/** A 2D affine transform mapping page pixels to destination-bitmap pixels, in
 * `android.graphics.Matrix#getValues()` order: `[mxx, mxy, mtx, myx, myy, mty]` — the
 * representation `tiff_core`'s native render call expects. */
public class TiffTransform(values: FloatArray) {
    init {
        require(values.size == 6) { "values must have 6 elements (mxx,mxy,mtx,myx,myy,mty), got ${values.size}" }
    }

    public val values: FloatArray = values.copyOf()
}
