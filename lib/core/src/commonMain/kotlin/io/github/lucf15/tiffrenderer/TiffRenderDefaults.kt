package io.github.lucf15.tiffrenderer

/** Fit-to-clip default transform: maps the full page onto exactly [clip]. */
internal fun defaultFitToClipTransform(pageWidth: Int, pageHeight: Int, clip: TiffRect): TiffTransform =
    TiffTransform(
        floatArrayOf(
            clip.width.toFloat() / pageWidth, 0f, clip.left.toFloat(),
            0f, clip.height.toFloat() / pageHeight, clip.top.toFloat(),
        ),
    )

/** [TiffRect] only guards against a degenerate rect; it has no notion of the destination
 * bitmap's size, so "does the clip fit inside the destination" is checked here instead. */
internal fun requireClipInBounds(clip: TiffRect, destWidth: Int, destHeight: Int) {
    require(clip.left >= 0 && clip.top >= 0 && clip.right <= destWidth && clip.bottom <= destHeight) {
        "destClip not in destination bounds"
    }
}
