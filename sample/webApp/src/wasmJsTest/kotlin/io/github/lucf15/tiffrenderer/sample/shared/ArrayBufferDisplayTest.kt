@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package io.github.lucf15.tiffrenderer.sample.shared

import androidx.compose.ui.graphics.toPixelMap
import io.github.lucf15.tiffrenderer.TiffRenderer
import io.github.lucf15.tiffrenderer.TiffSource
import io.github.lucf15.tiffrenderer.asArrayBuffer
import io.github.lucf15.tiffrenderer.TiffBitmap
import io.github.lucf15.tiffrenderer.use
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.webext.installPixelsFromArrayBuffer
import org.jetbrains.skiko.ExperimentalSkikoApi
import org.khronos.webgl.ArrayBuffer

@OptIn(ExperimentalSkikoApi::class)
class ArrayBufferDisplayTest {
    @Test
    fun rendersTiffThroughWorkerIntoComposeBitmap() = runTest {
        readySkiko()
        val bytes = Base64.decode(RGB_TIFF_BASE64)
        TiffRenderer.open(TiffSource.fromByteArray(bytes)).use { renderer ->
            val bitmap = renderer.renderPage(0, io.github.lucf15.tiffrenderer.RenderSize.Original).image
            val pixels = bitmap.toImageBitmap().toPixelMap()
            assertEquals(1f, pixels[0, 0].red)
            assertEquals(1f, pixels[1, 0].green)
            assertEquals(1f, pixels[0, 1].blue)
            assertEquals(1f, pixels[1, 1].alpha)
        }
    }

    @Test
    fun composeBridgePreservesColorAlphaAndOwnsItsPixels() = runTest {
        readySkiko()
        val bitmap = TiffBitmap.create(2, 2)
        fillPixels(bitmap.asArrayBuffer())
        val image = bitmap.toImageBitmap()
        val pixels = image.toPixelMap()
        assertEquals(1f, pixels[0, 0].red)
        assertEquals(0f, pixels[0, 0].green)
        assertEquals(1f, pixels[0, 0].alpha)
        assertEquals(1f, pixels[1, 0].green)
        assertTrue(kotlin.math.abs(pixels[1, 0].alpha - 128f / 255) < 0.01f)
        assertEquals(0f, pixels[0, 1].alpha)
        clearPixels(bitmap.asArrayBuffer())
        assertEquals(1f, image.toPixelMap()[0, 0].red)
        assertEquals(1f, image.toPixelMap()[0, 0].alpha)
    }

    @Test
    fun compareLegacyAndArrayBufferDisplayCosts() = runTest {
        readySkiko()
        for (size in listOf(256, 1024, 2048)) {
            val pixels = ArrayBuffer(size * size * 4)
            fillPixels(pixels)
            val info = ImageInfo(size, size, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
            suspend fun legacy(): Double {
                val start = now()
                val bytes = Base64.decode(encodePixels(pixels))
                val bitmap = Bitmap()
                try {
                    bitmap.allocPixels(info)
                    check(bitmap.installPixels(bytes.copyOf()))
                    bitmap.setImmutable()
                    assertEquals(0xFFFF0000.toInt(), bitmap.getColor(0, 0))
                } finally {
                    bitmap.close()
                }
                return now() - start
            }
            suspend fun direct(): Double {
                val start = now()
                val bitmap = Bitmap()
                try {
                    check(bitmap.installPixelsFromArrayBuffer(info, pixels, size * 4))
                    bitmap.setImmutable()
                    assertEquals(0xFFFF0000.toInt(), bitmap.getColor(0, 0))
                } finally {
                    bitmap.close()
                }
                return now() - start
            }
            repeat(2) { legacy(); direct() }
            val oldTimes = mutableListOf<Double>()
            val newTimes = mutableListOf<Double>()
            repeat(7) { iteration ->
                if (iteration % 2 == 0) {
                    oldTimes += legacy()
                    newTimes += direct()
                } else {
                    newTimes += direct()
                    oldTimes += legacy()
                }
            }
            println("DISPLAY_BENCHMARK ${size}x$size legacyMedianMs=${oldTimes.sorted()[3]} arrayBufferMedianMs=${newTimes.sorted()[3]}")
        }
    }
}

private suspend fun readySkiko(): Unit = suspendCoroutine { continuation ->
    awaitSkiko.then<JsAny?>(
        onFulfilled = { continuation.resume(Unit); null },
        onRejected = { continuation.resumeWithException(RuntimeException(it.toString())); null },
    )
}

private fun now(): Double = js("performance.now()")
private fun encodePixels(buffer: ArrayBuffer): String = js("new Uint8Array(buffer).toBase64()")
private fun clearPixels(buffer: ArrayBuffer): Unit = js("new Uint8Array(buffer).fill(0)")
private fun fillPixels(buffer: ArrayBuffer): Unit = js("""
(() => {
    const pixels = new Uint8Array(buffer);
    for (let i = 0; i < pixels.length; i += 16) {
        pixels.set([255, 0, 0, 255, 0, 255, 0, 128, 0, 0, 255, 0, 64, 128, 200, 255], i);
    }
})()
""")

private const val RGB_TIFF_BASE64 =
    "SUkqAAgAAAAJAAABAwABAAAAAgAAAAEBAwABAAAAAgAAAAIBAwADAAAAegAAAAMBAwABAAAAAQAAAAYBAwABAAAAAgAAABEBBAABAAAAgAAA" +
        "ABUBAwABAAAAAwAAABYBAwABAAAAAgAAABcBBAABAAAADAAAAAAAAAAIAAgACAD/AAAA/wAAAP9AgMg="
