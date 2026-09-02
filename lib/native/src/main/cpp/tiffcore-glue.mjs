// Runs on the main thread: actual libtiff/Emscripten calls happen in tiffcore-worker.mjs so a
// decode never blocks the browser's single UI thread (wasmJs has no background-thread dispatcher).
// One Worker per document, not shared: a crash only takes down its own document, and terminate()
// actually returns that document's WASM heap to the browser, since WebAssembly memory only grows.
export function createTiffCoreSession() {
    let worker = null;
    let nextRequestId = 0;
    const pending = new Map();
    let readyResolve;
    let readyPromise;

    function createReadyPromise() {
        readyPromise = new Promise((resolve) => { readyResolve = resolve; });
    }
    createReadyPromise();

    function failAllPending(error) {
        for (const { reject } of pending.values()) reject(error);
        pending.clear();
    }

    // A dead worker (load failure, uncaught abort, unstructured-cloneable message) would otherwise
    // leave every awaiting call hanging forever.
    function onWorkerUnusable(error) {
        failAllPending(error);
        worker?.terminate();
        worker = null;
        createReadyPromise();
    }

    function ensureLoaded() {
        if (!worker) {
            worker = new Worker(new URL("./tiffcore-worker.mjs", import.meta.url), { type: "module" });
            worker.onmessage = (event) => {
                const data = event.data;
                if (data.type === "ready") {
                    readyResolve();
                    return;
                }
                const entry = pending.get(data.id);
                if (entry) {
                    pending.delete(data.id);
                    // Raw pixel bytes arrive via zero-copy transfer; base64-encode here for Kotlin
                    // (no fast ByteArray bridge there) instead of inside the Worker.
                    entry.resolve(data.pixels ? { ...data, pixelsBase64: data.pixels.toBase64() } : data);
                }
            };
            worker.onerror = (event) => {
                onWorkerUnusable(new Error(`tiffcore worker crashed: ${event.message ?? event}`));
            };
            worker.onmessageerror = () => {
                onWorkerUnusable(new Error("tiffcore worker sent an unstructured-cloneable message"));
            };
        }
        return readyPromise;
    }

    function call(type, payload, transfer) {
        if (!worker) {
            return Promise.reject(new Error("tiffcore worker is not available (crashed or already terminated)"));
        }
        const id = nextRequestId++;
        return new Promise((resolve, reject) => {
            pending.set(id, { resolve, reject });
            worker.postMessage({ type, id, ...payload }, transfer ?? []);
        });
    }

    return {
        ensureLoaded,
        // Decodes here and transfers raw bytes to the Worker (zero-copy) instead of shipping the
        // larger base64 string over for it to decode again.
        openMemory: (base64) => {
            const bytes = Uint8Array.fromBase64(base64);
            return call("openMemory", { bytes }, [bytes.buffer]);
        },
        close: (doc) => call("close", { doc }),
        getPageCount: (doc) => call("getPageCount", { doc }),
        openPage: (doc, pageIndex) => call("openPage", { doc, pageIndex }),
        renderPage: (
            doc, pageIndex, width, height,
            clipLeft, clipTop, clipRight, clipBottom,
            matrix0, matrix1, matrix2, matrix3, matrix4, matrix5,
            renderMode,
        ) => call("renderPage", {
            doc, pageIndex, width, height,
            clipLeft, clipTop, clipRight, clipBottom,
            matrix0, matrix1, matrix2, matrix3, matrix4, matrix5,
            renderMode,
        }),
        retainRaster: (doc, pageIndex) => call("retainRaster", { doc, pageIndex }),
        releaseRaster: (doc) => call("releaseRaster", { doc }),
        // Safe with nothing in flight: TiffCoreHandle's Mutex serializes every call through the
        // same handle, so close() never races a render()/etc. on this session.
        terminate: () => {
            worker?.terminate();
            worker = null;
        },
    };
}
