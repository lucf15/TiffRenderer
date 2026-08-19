package io.github.lucf15.tiffrenderer

/** A seekable input to open with [TiffRenderer]. Opaque in commonMain: what backs it (a POSIX fd
 * on Android/iOS, a file path on JVM) is platform-specific, since there's no portable denominator
 * across all three (Windows has no raw-fd concept). Platform-specific constructors live in each
 * `actual`'s own companion. Single-use: consumed by the [TiffRenderer] it's passed to, even if
 * construction fails; passing the same instance again throws [IllegalStateException]. */
expect class TiffSource {
    /** Set by [TiffRenderer] the moment it starts consuming this source, before construction can
     * fail or succeed, so a second [TiffRenderer] over the same instance is rejected instead of
     * reusing an fd that may already be closed (or, on JVM's byte-array source, gone). */
    internal var consumed: Boolean

    /** Releases the platform resource this source owns, if any. Called once by
     * [TiffRenderer]'s close path. */
    internal fun release()
}
