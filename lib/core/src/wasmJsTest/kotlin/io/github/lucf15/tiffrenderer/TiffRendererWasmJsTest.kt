@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.lucf15.tiffrenderer

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.files.Blob

/** Runs the real Worker/Emscripten decode path in a headless browser (see `wasmJs { browser {
 * testTask { useKarma { useChromeHeadless() } } } }` in `lib/core/build.gradle.kts`), since
 * [com.goncalossilva.resources.Resource] used by `integrationTest`'s [Fixtures] has no wasmJs
 * target: fixtures are embedded as base64 instead, decoded the same way `TiffFilePicker.wasmJs.kt`
 * decodes a picked file's `data:` URL. */
@OptIn(ExperimentalEncodingApi::class)
class TiffRendererWasmJsTest {
    @Test
    fun blobSourceDecodesInTheWorkerAndReleasesTheInput() = runTest {
        val source = TiffSource.fromBlob(blobFromBase64(SINGLE_PAGE_RGB_TIF_BASE64))

        TiffRenderer.open(source).use { renderer ->

            assertEquals(1, renderer.pageCount())
            renderer.openPage(0).use { page ->
                val bitmap = createTiffBitmap(32, 24)
                page.render(bitmap)
                assertEquals(0xFF8040C8.toInt(), bitmap.pixelAt(0, 0))
            }
        }
        TiffRenderer.open(source).use { assertEquals(1, it.pageCount()) }
    }

    @Test
    fun invalidBlobSourcesFailWithoutPoisoningTheInputDescription() = runTest {
        val invalid = TiffSource.fromBlob(blobFromBase64("AAAA"))
        repeat(2) { assertFailsWith<TiffIOException> { TiffRenderer.open(invalid) } }
    }

    @Test
    fun blankBitmapAndByteExportsHaveIndependentStorage() {
        val bitmap = createTiffBitmap(2, 2)
        assertContentEquals(ByteArray(16), bitmap.copyPixels())
        fillBuffer(bitmap.asArrayBuffer(), 127)
        val exported = bitmap.copyPixels()
        exported[0] = 0
        assertEquals(127.toByte(), bitmap.copyPixels()[0])
        assertContentEquals(ByteArray(16), createTiffBitmap(2, 2).copyPixels())
    }

    @Test
    fun clippedRenderPreservesExistingPixelsAndFailedRenderPreservesTheBuffer() = runTest {
        TiffRenderer.open(TiffSource.fromByteArray(Base64.decode(SINGLE_PAGE_RGB_TIF_BASE64))).use { renderer ->
            renderer.openPage(0).use { page ->
                val bitmap = createTiffBitmap(32, 24)
                fillBuffer(bitmap.asArrayBuffer(), 77)
                page.render(bitmap, destClip = TiffRect(8, 6, 24, 18))
                val pixels = Uint8Array(bitmap.asArrayBuffer())
                for (y in 0 until 24) {
                    for (x in 0 until 32) {
                        val expected = if (x in 8 until 24 && y in 6 until 18) listOf(128, 64, 200, 255)
                            else listOf(77, 77, 77, 77)
                        for (channel in 0..3) {
                            assertEquals(expected[channel], pixels[(y * 32 + x) * 4 + channel].toInt() and 0xFF,
                                "pixel ($x, $y), channel $channel")
                        }
                    }
                }
                val before = bitmap.copyPixels()
                assertFailsWith<IllegalArgumentException> {
                    page.render(bitmap, transform = TiffTransform(FloatArray(6)))
                }
                assertContentEquals(before, bitmap.copyPixels())
            }
        }
    }

    @Test
    fun transformedRenderLeavesNewBitmapMarginsTransparent() = runTest {
        TiffRenderer.open(TiffSource.fromByteArray(Base64.decode(SINGLE_PAGE_RGB_TIF_BASE64))).use { renderer ->
            renderer.openPage(0).use { page ->
                val bitmap = createTiffBitmap(40, 32)
                page.render(bitmap, transform = TiffTransform(floatArrayOf(1f, 0f, 4f, 0f, 1f, 3f)))
                assertEquals(0, bitmap.pixelAt(0, 0))
                assertEquals(0, bitmap.pixelAt(39, 31))
                assertEquals(0xFF8040C8.toInt(), bitmap.pixelAt(5, 4))
            }
        }
    }

    @Test
    fun opensDecodesAndClosesThroughTheRealWorker() = runTest {
        val renderer = TiffRenderer.open(TiffSource.fromByteArray(Base64.decode(SINGLE_PAGE_RGB_TIF_BASE64)))
        assertEquals(1, renderer.pageCount())

        renderer.openPage(0).use { page ->
            assertEquals(32, page.width)
            assertEquals(24, page.height)

            val bitmap = createTiffBitmap(page.width, page.height)
            page.render(bitmap)

            val bytes = bitmap.copyPixels()
            assertEquals(page.width * page.height * 4, bytes.size)
            assertTrue(bytes.any { it != 0.toByte() }, "decoded pixels should not be all zero")
            for (i in bytes.indices step 4) {
                assertEquals(255.toByte(), bytes[i + 3], "pixel ${i / 4} alpha")
            }
        }

        renderer.close()
    }

    /** Deflate/ZIP needs Emscripten's zlib port wired in specifically for wasmJs (see
     * `build-wasm.sh`'s `embuilder build zlib` step); every other supported codec is built the
     * same way as on every other platform, so this is the one codec worth a dedicated wasmJs
     * regression test. */
    @Test
    fun decodesDeflateCompressedPages() = runTest {
        val renderer = TiffRenderer.open(TiffSource.fromByteArray(Base64.decode(SUPPORTED_DEFLATE_TIF_BASE64)))
        assertEquals(3, renderer.pageCount())

        renderer.openPage(0).use { page ->
            assertEquals(64, page.width)
            assertEquals(48, page.height)

            val bitmap = createTiffBitmap(page.width, page.height)
            page.render(bitmap)

            val bytes = bitmap.copyPixels()
            assertEquals(page.width * page.height * 4, bytes.size)
            assertTrue(bytes.any { it != 0.toByte() }, "decoded pixels should not be all zero")
        }

        renderer.close()
    }
}

private fun fillBuffer(buffer: ArrayBuffer, value: Int): Unit = js("new Uint8Array(buffer).fill(value)")

private fun blobFromBase64(base64: String): Blob = js("new Blob([Uint8Array.fromBase64(base64)])")

private const val SINGLE_PAGE_RGB_TIF_BASE64 =
    "SUkqAAgAAAAJAAABAwABAAAAIAAAAAEBAwABAAAAGAAAAAIBAwABAAAACAAAAAMBAwABAAAAAQAAAAYBAwABAAAAAgAAABEBBAABAAAAegAAABUBAwABAAAA" +
    "AwAAABYBAwABAAAAGAAAABcBBAABAAAAAAkAAAAAAACAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiA" +
    "QMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMiAQMg="

private const val SUPPORTED_DEFLATE_TIF_BASE64 =
    "SUkqAEAAAAB4nO3CMQ0AAAACoDr2z2MYQ/jCaFJVVVVVVVVV9TuE6061eJy7o6FxZxSNolE0igYCAQCu6sIQAAsAAAEDAAEAAABAAAAAAQEDAAEAAAAwAAAA" +
    "AgEDAAMAAADKAAAAAwEDAAEAAAAIAAAABgEDAAEAAAACAAAAEQEEAAIAAADYAAAAEgEDAAEAAAABAAAAFQEDAAEAAAADAAAAFgEDAAEAAAAqAAAAFwEEAAIA" +
    "AADQAAAAHAEDAAEAAAABAAAAGAEAAAgACAAIACIAAAAVAAAACAAAACoAAAB4nO3CMQ0AAAwDIDsVUf+prInYCyFrVFVVVVVVVdXfA1BzTrV4nNM4YaMxikbR" +
    "KBpFA4EAgtvCEAsAAAEDAAEAAABAAAAAAQEDAAEAAAAwAAAAAgEDAAMAAACiAQAAAwEDAAEAAAAIAAAABgEDAAEAAAACAAAAEQEEAAIAAACwAQAAEgEDAAEA" +
    "AAABAAAAFQEDAAEAAAADAAAAFgEDAAEAAAAqAAAAFwEEAAIAAACoAQAAHAEDAAEAAAABAAAA8AEAAAgACAAIACQAAAAUAAAA4AAAAAQBAAB4nO3CQREAAAwC" +
    "oDpmMrthFmJfONJFVVVVVVVVVf09azpb03ic04i6ozGKRtEoGkUDgQCCMw0fAAsAAAEDAAEAAABAAAAAAQEDAAEAAAAwAAAAAgEDAAMAAAB6AgAAAwEDAAEA" +
    "AAAIAAAABgEDAAEAAAACAAAAEQEEAAIAAACIAgAAEgEDAAEAAAABAAAAFQEDAAEAAAADAAAAFgEDAAEAAAAqAAAAFwEEAAIAAACAAgAAHAEDAAEAAAABAAAA" +
    "AAAAAAgACAAIACMAAAAUAAAAuAEAANsBAAA="
