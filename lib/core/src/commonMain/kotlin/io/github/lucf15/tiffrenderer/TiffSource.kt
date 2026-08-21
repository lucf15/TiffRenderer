package io.github.lucf15.tiffrenderer

/** A seekable input to open with [TiffRenderer]. Opaque in commonMain since what backs it (a fd on
 * Android/iOS, a file path on JVM) is platform-specific; platform constructors live in each
 * `actual`'s own companion. Single-use: reusing an already-consumed instance throws
 * [IllegalStateException]. */
public expect class TiffSource {
    /** Called by [TiffRenderer] on construction, rejecting a second [TiffRenderer] over the same
     * source instead of reusing a possibly-already-closed fd. Compare-and-set: `true` only for
     * the caller that wins a race with another [TiffRenderer] over the same source. */
    internal fun markConsumed(): Boolean

    /** Releases the platform resource this source owns, if any. Idempotent, and safe to call
     * directly if this instance is never handed to a [TiffRenderer] (e.g. a fd-backed source
     * constructed but then discarded) — otherwise called once by [TiffRenderer]'s close path. */
    public fun release()
}
