// Runs on the main thread: actual libtiff/Emscripten calls happen in tiffcore-worker.mjs so a
// decode never blocks the browser's single UI thread (wasmJs has no background-thread dispatcher).
// One Worker per document, not shared: a crash only takes down its own document, and terminate()
// actually returns that document's WASM heap to the browser, since WebAssembly memory only grows.
export function createTiffCoreSession() {
    let worker = null;
    let nextRequestId = 0;
    const pending = new Map();
    let readyResolve;
    let readyReject;
    let failure = null;
    let startupTimer;
    const readyPromise = new Promise((resolve, reject) => { readyResolve = resolve; readyReject = reject; });
    readyPromise.catch(() => {});

    function failAllPending(error) {
        for (const { reject } of pending.values()) reject(error);
        pending.clear();
    }

    // A dead worker (load failure, uncaught abort, unstructured-cloneable message) would otherwise
    // leave every awaiting call hanging forever.
    function onWorkerUnusable(error) {
        if (failure) return;
        failure = error;
        clearTimeout(startupTimer);
        readyReject(error);
        failAllPending(error);
        worker?.terminate();
        worker = null;
    }

    function ensureLoaded() {
        if (failure) return Promise.reject(failure);
        if (!worker) {
            try {
                worker = new Worker(new URL("./tiffcore-worker.mjs", import.meta.url), { type: "module" });
            } catch (error) {
                onWorkerUnusable(error);
                return readyPromise;
            }
            startupTimer = setTimeout(() => onWorkerUnusable(new Error("tiffcore worker startup timed out")), 30000);
            worker.onmessage = (event) => {
                const data = event.data;
                if (data.type === "ready") {
                    clearTimeout(startupTimer);
                    readyResolve();
                    return;
                }
                if (data.type === "initError") {
                    onWorkerUnusable(new Error(`cannot load tiffcore worker: ${data.message}`));
                    return;
                }
                const entry = pending.get(data.id);
                if (entry) {
                    pending.delete(data.id);
                    entry.resolve(data);
                }
            };
            worker.onerror = (event) => {
                event.preventDefault?.();
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
            try {
                worker.postMessage({ type, id, ...payload }, transfer ?? []);
            } catch (error) {
                pending.delete(id);
                reject(error);
            }
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
        openBlob: (blob) => call("openBlob", { blob }),
        close: (doc) => call("close", { doc }),
        setLimits: (doc, decoded, working) => call("setLimits", { doc, decoded, working }),
        getPageCount: (doc) => call("getPageCount", { doc }),
        openPage: (doc, pageIndex) => call("openPage", { doc, pageIndex }),
        renderPage: (
            doc, pageIndex, width, height,
            clipLeft, clipTop, clipRight, clipBottom,
            matrix0, matrix1, matrix2, matrix3, matrix4, matrix5,
            renderMode,
            destination,
        ) => call("renderPage", {
            doc, pageIndex, width, height,
            clipLeft, clipTop, clipRight, clipBottom,
            matrix0, matrix1, matrix2, matrix3, matrix4, matrix5,
            renderMode,
            destination,
        }),
        retainRaster: (doc, pageIndex) => call("retainRaster", { doc, pageIndex }),
        releaseRaster: (doc) => call("releaseRaster", { doc }),
        terminate: () => onWorkerUnusable(new Error("tiffcore worker session terminated")),
    };
}
