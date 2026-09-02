package io.github.lucf15.tiffrenderer

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Runs the real Worker/Emscripten decode path in a headless browser (see `wasmJs { browser {
 * testTask { useKarma { useChromeHeadless() } } } }` in `lib/core/build.gradle.kts`), since
 * [com.goncalossilva.resources.Resource] used by `integrationTest`'s [Fixtures] has no wasmJs
 * target: fixtures are embedded as base64 instead, decoded the same way `TiffFilePicker.wasmJs.kt`
 * decodes a picked file's `data:` URL. */
@OptIn(ExperimentalEncodingApi::class)
class TiffRendererWasmJsTest {
    @Test
    fun opensDecodesAndClosesThroughTheRealWorker() = runTest {
        val renderer = TiffRenderer.open(TiffSource.fromByteArray(Base64.decode(SINGLE_PAGE_RGB_TIF_BASE64)))
        assertEquals(1, renderer.pageCount())

        renderer.openPage(0).use { page ->
            assertEquals(32, page.width)
            assertEquals(24, page.height)

            val bitmap = createTiffBitmap(page.width, page.height)
            page.render(bitmap)

            val bytes = bitmap.toByteArray()
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

            val bytes = bitmap.toByteArray()
            assertEquals(page.width * page.height * 4, bytes.size)
            assertTrue(bytes.any { it != 0.toByte() }, "decoded pixels should not be all zero")
        }

        renderer.close()
    }
}

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
