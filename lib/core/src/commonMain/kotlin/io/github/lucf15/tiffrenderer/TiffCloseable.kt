package io.github.lucf15.tiffrenderer

/** [kotlin.io.use] equivalent for [TiffRenderer]: that stdlib function only works with
 * [AutoCloseable], which [TiffRenderer] can't implement since `close()` is `suspend`. */
public suspend inline fun <R> TiffRenderer.use(block: suspend (TiffRenderer) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        val cause = exception
        if (cause == null) {
            close()
        } else {
            try {
                close()
            } catch (closeException: Throwable) {
                cause.addSuppressed(closeException)
            }
        }
    }
}

/** [kotlin.io.use] equivalent for [TiffPage]; see [TiffRenderer.use]. */
public suspend inline fun <R> TiffPage.use(block: suspend (TiffPage) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        val cause = exception
        if (cause == null) {
            close()
        } else {
            try {
                close()
            } catch (closeException: Throwable) {
                cause.addSuppressed(closeException)
            }
        }
    }
}
