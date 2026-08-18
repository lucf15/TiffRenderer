package io.github.lucf15.tiffrenderer

/** A seekable input to open with [TiffRenderer]. Opaque in commonMain: what backs it (a POSIX fd
 * on Android/iOS, a file path on JVM) is platform-specific, since there's no portable denominator
 * across all three (Windows has no raw-fd concept). Platform-specific constructors live in each
 * `actual`'s own companion. */
expect class TiffSource {
    /** Releases the platform resource this source owns, if any. Called once by
     * [TiffRenderer]'s close path. */
    internal fun release()
}
