package io.github.lucf15.tiffrenderer

/** Selects the resampling filter used by [TiffPage.render]. */
enum class TiffRenderMode {
    /** Bilinear: smooths zoomed-in blockiness, for on-screen display. */
    FOR_DISPLAY,

    /** Nearest-neighbor: exact source pixels, for printing. */
    FOR_PRINT,
}
