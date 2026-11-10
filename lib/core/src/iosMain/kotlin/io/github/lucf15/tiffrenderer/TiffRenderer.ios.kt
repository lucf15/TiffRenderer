@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.lucf15.tiffrenderer

import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGDataProviderCreateWithData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.CoreGraphics.CGColorRenderingIntent
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import platform.posix.free
import platform.posix.malloc
import platform.posix.memcpy

@Throws(kotlin.coroutines.cancellation.CancellationException::class, TiffIOException::class, IllegalArgumentException::class)
public suspend fun TiffRenderer.Companion.open(url: NSURL, options: TiffOptions = TiffOptions()): TiffRenderer = open(TiffSource.fromUrl(url), options)

public fun TiffBitmap.toUIImage(): UIImage {
    val length = pixels.size.toULong() * 4u
    val data = malloc(length) ?: throw TiffIOException("Cannot allocate image", TiffError.RESOURCE_LIMIT)
    val provider = try {
        pixels.usePinned { memcpy(data, it.addressOf(0), length) }
        CGDataProviderCreateWithData(null, data, length, staticCFunction { _: COpaquePointer?, bytes: COpaquePointer?, _: ULong -> free(bytes) })
    } catch (t: Throwable) { free(data); throw t }
    if (provider == null) { free(data); throw TiffIOException("Cannot create pixel provider") }
    try {
        val color = CGColorSpaceCreateDeviceRGB() ?: throw TiffIOException("Cannot create color space")
        try {
            val image = CGImageCreate(width.toULong(), height.toULong(), 8u, 32u, width.toULong() * 4u, color,
                kCGBitmapByteOrder32Big or CGImageAlphaInfo.kCGImageAlphaLast.value, provider, null, false, CGColorRenderingIntent.kCGRenderingIntentDefault)
                ?: throw TiffIOException("Cannot create CGImage")
            try { return UIImage.imageWithCGImage(image) } finally { CGImageRelease(image) }
        } finally { CGColorSpaceRelease(color) }
    } finally { CGDataProviderRelease(provider) }
}

public class AppleTiffRenderRequest internal constructor(url: NSURL, pageIndex: Int, maxWidth: Int, maxHeight: Int) {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private val result = scope.async { AppleTiffRenderer.render(url, pageIndex, maxWidth, maxHeight) }
    init { result.invokeOnCompletion { scope.cancel() } }
    public fun cancel() { result.cancel() }
    @Throws(kotlin.coroutines.cancellation.CancellationException::class, TiffIOException::class, IllegalArgumentException::class)
    public suspend fun awaitImage(): UIImage = result.await()
}

public class AppleTiffRenderer private constructor() {
    public companion object {
        public fun request(url: NSURL, pageIndex: Int, maxWidth: Int, maxHeight: Int): AppleTiffRenderRequest =
            AppleTiffRenderRequest(url, pageIndex, maxWidth, maxHeight)

        @Throws(kotlin.coroutines.cancellation.CancellationException::class, TiffIOException::class, IllegalArgumentException::class)
        public suspend fun render(url: NSURL, pageIndex: Int, maxWidth: Int, maxHeight: Int): UIImage =
            TiffRenderer.withDocument(TiffSource.fromUrl(url)) {
                it.renderPage(pageIndex, RenderSize.fit(maxWidth, maxHeight)).image.toUIImage()
            }
    }
}
