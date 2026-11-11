package io.github.lucf15.tiffrenderer

public data class TiffLimits(
    public val maxInputBytes: Long = 128L * 1024 * 1024,
    public val maxDecodedBytes: Long = 128L * 1024 * 1024,
    public val maxOutputBytes: Long = 64L * 1024 * 1024,
    public val maxWorkingBytes: Long = 256L * 1024 * 1024,
) {
    init {
        require(maxInputBytes in 1..256L * 1024 * 1024)
        require(maxDecodedBytes in 4..256L * 1024 * 1024)
        require(maxOutputBytes in 4..256L * 1024 * 1024)
        require(maxWorkingBytes in (NATIVE_RESERVE_BYTES + 4)..512L * 1024 * 1024)
    }
}

public enum class TiffDecodePolicy { STRICT, ALLOW_PARTIAL }

public data class TiffOptions(
    public val limits: TiffLimits = TiffLimits(),
    public val decodePolicy: TiffDecodePolicy = TiffDecodePolicy.STRICT,
    public val cacheBytes: Long = 0,
) {
    init { require(cacheBytes in 0..limits.maxDecodedBytes) }
}

public data class TiffPageInfo(public val index: Int, public val width: Int, public val height: Int) {
    init { require(index >= 0 && width > 0 && height > 0) }
    public val rasterBytes: Long get() {
        val pixels = width.toLong() * height
        return if (pixels > Long.MAX_VALUE / 4) Long.MAX_VALUE else pixels * 4
    }
}

public enum class TiffWarning { PARTIAL_DECODE }

public data class TiffRenderResult(public val image: TiffBitmap, public val warnings: Set<TiffWarning>)

public sealed class RenderSize {
    public data class Fit(public val maxWidth: Int, public val maxHeight: Int, public val allowUpscale: Boolean = false) : RenderSize() {
        init { require(maxWidth > 0 && maxHeight > 0) }
    }
    public data object Original : RenderSize()

    public companion object {
        @kotlin.jvm.JvmStatic
        @kotlin.jvm.JvmOverloads
        public fun fit(maxWidth: Int, maxHeight: Int, allowUpscale: Boolean = false): RenderSize =
            Fit(maxWidth, maxHeight, allowUpscale)
    }
}
