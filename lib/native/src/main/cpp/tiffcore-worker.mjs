import createTiffCoreModule from "./tiffcore_module.mjs";

const ERR_BUF_SIZE = 512;
let Module = null;

function errorMessage(ptr) { return Module.UTF8ToString(ptr); }

function mallocChecked(size) {
    const ptr = Module._malloc(size);
    if (ptr === 0) throw new Error("tiffcore worker: out of memory (malloc failed)");
    return ptr;
}

function handleOpenMemory(d) {
    // Already decoded and transferred zero-copy by tiffcore-glue.mjs, not sent as base64.
    const bytes = d.bytes;
    const allocated = [];
    try {
        const dataPtr = mallocChecked(bytes.length);
        allocated.push(dataPtr);
        Module.HEAPU8.set(bytes, dataPtr);
        const outDocPtr = mallocChecked(4);
        allocated.push(outDocPtr);
        const errBufPtr = mallocChecked(ERR_BUF_SIZE);
        allocated.push(errBufPtr);
        const status = Module._tiffcore_open_memory(dataPtr, BigInt(bytes.length), outDocPtr, errBufPtr, ERR_BUF_SIZE);
        const doc = Module.HEAPU32[outDocPtr >> 2];
        const message = status !== 0 ? errorMessage(errBufPtr) : null;
        return { status, doc, message };
    } finally {
        for (const ptr of allocated) Module._free(ptr);
    }
}

function handleClose(d) {
    Module._tiffcore_close(d.doc);
    return {};
}

function handleGetPageCount(d) {
    return { pageCount: Module._tiffcore_get_page_count(d.doc) };
}

function handleOpenPage(d) {
    const allocated = [];
    try {
        const outWidthPtr = mallocChecked(4);
        allocated.push(outWidthPtr);
        const outHeightPtr = mallocChecked(4);
        allocated.push(outHeightPtr);
        const errBufPtr = mallocChecked(ERR_BUF_SIZE);
        allocated.push(errBufPtr);
        const status = Module._tiffcore_open_page(d.doc, d.pageIndex, outWidthPtr, outHeightPtr, errBufPtr, ERR_BUF_SIZE);
        const width = Module.HEAPU32[outWidthPtr >> 2];
        const height = Module.HEAPU32[outHeightPtr >> 2];
        const message = status !== 0 ? errorMessage(errBufPtr) : null;
        return { status, width, height, message };
    } finally {
        for (const ptr of allocated) Module._free(ptr);
    }
}

function handleRenderPage(d) {
    const pixelCount = d.width * d.height;
    const allocated = [];
    try {
        const dstPixelsPtr = mallocChecked(pixelCount * 4);
        allocated.push(dstPixelsPtr);
        const matrixPtr = mallocChecked(6 * 4);
        allocated.push(matrixPtr);
        const errBufPtr = mallocChecked(ERR_BUF_SIZE);
        allocated.push(errBufPtr);
        const matrix = [d.matrix0, d.matrix1, d.matrix2, d.matrix3, d.matrix4, d.matrix5];
        for (let i = 0; i < 6; i++) Module.HEAPF32[(matrixPtr >> 2) + i] = matrix[i];

        const status = Module._tiffcore_render_page(
            d.doc, d.pageIndex, dstPixelsPtr, d.width, d.width, d.height,
            d.clipLeft, d.clipTop, d.clipRight, d.clipBottom, matrixPtr, d.renderMode, errBufPtr, ERR_BUF_SIZE,
        );
        let pixels = null;
        let message = null;
        if (status === 0 || status === 4) {
            // slice(), not subarray(): a view into this module's own memory can't be transferred
            // (that would detach the Worker's whole WASM heap), and dstPixelsPtr is freed below.
            pixels = Module.HEAPU8.slice(dstPixelsPtr, dstPixelsPtr + pixelCount * 4);
        } else {
            message = errorMessage(errBufPtr);
        }
        return { status, pixels, message };
    } finally {
        for (const ptr of allocated) Module._free(ptr);
    }
}

function handleRetainRaster(d) {
    const errBufPtr = mallocChecked(ERR_BUF_SIZE);
    try {
        const status = Module._tiffcore_retain_raster(d.doc, d.pageIndex, errBufPtr, ERR_BUF_SIZE);
        const message = (status !== 0 && status !== 4) ? errorMessage(errBufPtr) : null;
        return { status, message };
    } finally {
        Module._free(errBufPtr);
    }
}

function handleReleaseRaster(d) {
    Module._tiffcore_release_raster(d.doc);
    return {};
}

const handlers = {
    openMemory: handleOpenMemory,
    close: handleClose,
    getPageCount: handleGetPageCount,
    openPage: handleOpenPage,
    renderPage: handleRenderPage,
    retainRaster: handleRetainRaster,
    releaseRaster: handleReleaseRaster,
};

const readyPromise = createTiffCoreModule().then((module) => {
    Module = module;
    Module._tiffcore_global_init();
    self.postMessage({ type: "ready" });
});

self.onmessage = async (event) => {
    await readyPromise;
    const d = event.data;
    try {
        const result = handlers[d.type](d);
        const transfer = result.pixels ? [result.pixels.buffer] : [];
        self.postMessage({ id: d.id, ...result }, transfer);
    } catch (e) {
        self.postMessage({ id: d.id, status: -1, message: String(e) });
    }
};
