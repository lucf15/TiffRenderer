package io.github.lucf15.tiffrenderer

import java.io.File

public actual class TiffSource private constructor(
    internal val path: String?,
    internal var bytes: ByteArray?,
) {
    private val consumedFlag = OnceFlag()
    private val releasedFlag = OnceFlag()

    internal actual fun markConsumed(): Boolean = consumedFlag.trySet()

    public actual fun release() {
        // No real resource to free here (path/bytes need no cleanup); keeps the same once-only contract Android/iOS enforce.
        releasedFlag.trySet()
    }

    public companion object {
        public fun fromPath(path: String): TiffSource = TiffSource(path, null)

        public fun fromFile(file: File): TiffSource = TiffSource(file.absolutePath, null)

        public fun fromByteArray(bytes: ByteArray): TiffSource = TiffSource(null, bytes)
    }
}
