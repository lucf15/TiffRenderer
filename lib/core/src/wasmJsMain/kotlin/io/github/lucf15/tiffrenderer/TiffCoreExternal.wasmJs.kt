@file:JsModule("./tiffcore-glue.mjs")
@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.lucf15.tiffrenderer

import kotlin.js.Promise

internal external interface OpenMemoryResponse : JsAny {
    val status: Int
    val doc: Int
    val message: String?
}

internal external interface PageCountResponse : JsAny {
    val pageCount: Int
}

internal external interface OpenPageResponse : JsAny {
    val status: Int
    val width: Int
    val height: Int
    val message: String?
}

internal external interface RenderPageResponse : JsAny {
    val status: Int
    val pixelsBase64: String?
    val message: String?
}

internal external interface StatusResponse : JsAny {
    val status: Int
    val message: String?
}

/** One dedicated Worker per document, spawned by [createTiffCoreSession]: a crash stays isolated
 * to the document that owns it, and [terminate] returns that worker's WASM heap to the browser,
 * since WebAssembly memory only grows and closing the document alone can't reclaim it. */
internal external interface TiffCoreSession : JsAny {
    fun ensureLoaded(): Promise<JsAny?>

    fun openMemory(base64: String): Promise<OpenMemoryResponse>

    fun close(doc: Int): Promise<JsAny?>

    fun getPageCount(doc: Int): Promise<PageCountResponse>

    fun openPage(doc: Int, pageIndex: Int): Promise<OpenPageResponse>

    fun renderPage(
        doc: Int,
        pageIndex: Int,
        width: Int,
        height: Int,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
        matrix0: Float,
        matrix1: Float,
        matrix2: Float,
        matrix3: Float,
        matrix4: Float,
        matrix5: Float,
        renderMode: Int,
    ): Promise<RenderPageResponse>

    fun retainRaster(doc: Int, pageIndex: Int): Promise<StatusResponse>

    fun releaseRaster(doc: Int): Promise<JsAny?>

    /** Terminates this session's Worker; a no-op if it's already gone. */
    fun terminate()
}

internal external fun createTiffCoreSession(): TiffCoreSession
