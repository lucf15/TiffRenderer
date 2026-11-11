package io.github.lucf15.tiffrenderer

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

public suspend fun TiffRenderer.Companion.open(path: Path, options: TiffOptions = TiffOptions()): TiffRenderer = open(TiffSource.fromPath(path), options)
public suspend fun TiffRenderer.Companion.open(file: File, options: TiffOptions = TiffOptions()): TiffRenderer = open(file.toPath(), options)

public fun TiffBitmap.toBufferedImage(): BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
    it.setRGB(0, 0, width, height, toIntArray(), 0, width)
}

/** Blocks its caller; intended for Java and non-UI JVM code. */
public class BlockingTiffRenderer private constructor(private val document: TiffRenderer) : AutoCloseable {
    @Throws(TiffIOException::class)
    public fun pageCount(): Int = runBlocking { document.pageCount() }
    @Throws(TiffIOException::class)
    public fun pageInfo(index: Int): TiffPageInfo = runBlocking { document.pageInfo(index) }
    @Throws(TiffIOException::class)
    public fun renderPage(index: Int, size: RenderSize): TiffRenderResult = runBlocking { document.renderPage(index, size) }
    @JvmOverloads
    @Throws(TiffIOException::class)
    public fun renderImage(index: Int, maxWidth: Int, maxHeight: Int = Int.MAX_VALUE): BufferedImage =
        renderPage(index, RenderSize.fit(maxWidth, maxHeight)).image.toBufferedImage()
    @Throws(TiffIOException::class)
    override fun close(): Unit = runBlocking { document.close() }
    public companion object {
        @JvmStatic
        @JvmOverloads
        @Throws(TiffIOException::class)
        public fun open(path: Path, options: TiffOptions = TiffOptions()): BlockingTiffRenderer =
            BlockingTiffRenderer(runBlocking { TiffRenderer.open(path, options) })
    }
}

public fun TiffRenderer.Companion.renderAsync(path: Path, pageIndex: Int, size: RenderSize, options: TiffOptions = TiffOptions()): CompletableFuture<TiffRenderResult> {
    val future = CompletableFuture<TiffRenderResult>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    future.whenComplete { _, _ -> scope.cancel() }
    scope.launch {
        try { future.complete(render(TiffSource.fromPath(path), pageIndex, size, options)) }
        catch (t: Throwable) { future.completeExceptionally(t) }
    }
    return future
}

public object TiffFutures {
    @JvmStatic
    @JvmOverloads
    public fun render(path: Path, pageIndex: Int, size: RenderSize, options: TiffOptions = TiffOptions()): CompletableFuture<TiffRenderResult> =
        TiffRenderer.renderAsync(path, pageIndex, size, options)
}
