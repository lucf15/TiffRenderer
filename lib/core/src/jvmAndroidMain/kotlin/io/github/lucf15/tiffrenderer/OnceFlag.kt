package io.github.lucf15.tiffrenderer

import java.util.concurrent.atomic.AtomicBoolean

/** A boolean flag that flips `true` exactly once; [trySet] returns whether this call was the one
 * that flipped it. */
internal class OnceFlag {
    private val flag = AtomicBoolean(false)

    fun trySet(): Boolean = flag.compareAndSet(false, true)
}
