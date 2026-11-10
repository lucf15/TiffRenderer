package io.github.lucf15.tiffrenderer

public enum class TiffError { INVALID_DATA, RESOURCE_LIMIT, PARTIAL_DECODE, INPUT_UNAVAILABLE, WORKER_UNAVAILABLE }

public class TiffIOException(
    message: String,
    public val reason: TiffError = TiffError.INVALID_DATA,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
