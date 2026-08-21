package io.github.lucf15.tiffrenderer

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect

public fun Rect.toTiffRect(): TiffRect = TiffRect(left, top, right, bottom)

/** Converts an affine [Matrix] to [TiffTransform]. Throws [IllegalArgumentException] if [this]
 * has a non-identity perspective row, since [TiffTransform] can only represent an affine
 * transform. */
public fun Matrix.toTiffTransform(): TiffTransform {
    val values = FloatArray(9)
    getValues(values)
    require(values[6] == 0f && values[7] == 0f && values[8] == 1f) {
        "TiffTransform cannot represent a non-affine (perspective) Matrix"
    }
    return TiffTransform(floatArrayOf(values[0], values[1], values[2], values[3], values[4], values[5]))
}

/** Overload of [TiffPage.render] taking platform [Bitmap]/[Rect]/[Matrix] types directly. */
public fun TiffPage.render(
    destination: Bitmap,
    destClip: Rect? = null,
    transform: Matrix? = null,
    renderMode: TiffRenderMode = TiffRenderMode.FOR_DISPLAY,
    onPartialDecode: (() -> Unit)? = null,
): Unit = render(
    TiffBitmap(destination), destClip?.toTiffRect(), transform?.toTiffTransform(), renderMode, onPartialDecode,
)
