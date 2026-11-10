package io.github.lucf15.tiffrenderer

internal class PixelTestPage(internal val renderer: TiffRenderer, private val info: TiffPageInfo) {
    val width: Int get() = info.width
    val height: Int get() = info.height
    val index: Int get() = info.index
    private var closed = false
    suspend fun render(destination: TiffBitmap, destClip: TiffRect? = null, transform: TiffTransform? = null,
        renderMode: TiffSampling = TiffSampling.SMOOTH, onPartialDecode: (() -> Unit)? = null) {
        check(!closed)
        val warnings = renderer.renderInto(index, destination, destClip, transform, renderMode)
        if (TiffWarning.PARTIAL_DECODE in warnings) onPartialDecode?.invoke()
    }
    suspend fun retainRaster(onPartialDecode: (() -> Unit)? = null) {
        check(!closed)
        if (TiffWarning.PARTIAL_DECODE in renderer.renderPage(index, RenderSize.fit(1, 1)).warnings) onPartialDecode?.invoke()
    }
    suspend fun close() { closed = true }
}

internal suspend fun TiffRenderer.openPage(index: Int): PixelTestPage = PixelTestPage(this, pageInfo(index))
internal suspend fun <R> PixelTestPage.use(block: suspend (PixelTestPage) -> R): R = try { block(this) } finally { close() }
