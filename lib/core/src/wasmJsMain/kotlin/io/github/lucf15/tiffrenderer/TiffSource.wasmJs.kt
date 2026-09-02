package io.github.lucf15.tiffrenderer

/** Browsers expose no filesystem path/fd, only bytes (e.g. from a `File`'s `ArrayBuffer`), so this
 * is byte-array-based like [TiffSource.fromByteArray] on JVM, without the fd/path options
 * Android/iOS/JVM also offer. */
public actual class TiffSource private constructor(internal var bytes: ByteArray?) {
    private var consumedFlag = false
    private var releasedFlag = false

    internal actual fun markConsumed(): Boolean {
        if (consumedFlag) return false
        consumedFlag = true
        return true
    }

    public actual fun release() {
        releasedFlag = true
    }

    public companion object {
        public fun fromByteArray(bytes: ByteArray): TiffSource = TiffSource(bytes)
    }
}
