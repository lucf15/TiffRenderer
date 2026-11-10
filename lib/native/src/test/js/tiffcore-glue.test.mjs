import test, { afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { createTiffCoreSession } from '../../main/cpp/tiffcore-glue.mjs';

const originalWorker = globalThis.Worker;
const sessions = [];
let worker;
class FakeWorker {
    constructor() { worker = this; this.terminated = false; }
    terminate() { this.terminated = true; }
    postMessage(message) { this.lastMessage = message; }
    receive(data) { this.onmessage({ data }); }
}
function session() {
    globalThis.Worker = FakeWorker;
    const value = createTiffCoreSession();
    sessions.push(value);
    return value;
}
afterEach(() => {
    for (const value of sessions.splice(0)) value.terminate();
    globalThis.Worker = originalWorker;
});

test('startup crash rejects readiness and cannot resurrect the session', async () => {
    const value = session();
    const ready = value.ensureLoaded();
    let prevented = false;
    worker.onerror({ message: 'startup failed', preventDefault() { prevented = true; } });
    await assert.rejects(ready, /startup failed/);
    assert.equal(prevented, true);
    assert.equal(worker.terminated, true);
    await assert.rejects(value.ensureLoaded(), /startup failed/);
});

test('synchronous Worker construction failure rejects readiness', async () => {
    const value = session();
    globalThis.Worker = class { constructor() { throw new Error('worker creation blocked'); } };
    await assert.rejects(value.ensureLoaded(), /worker creation blocked/);
});

test('native module initialization failure rejects readiness', async () => {
    const value = session();
    const ready = value.ensureLoaded();
    worker.receive({ type: 'initError', message: 'wasm download failed' });
    await assert.rejects(ready, /wasm download failed/);
    assert.equal(worker.terminated, true);
});

test('startup timeout rejects readiness and terminates the worker', async () => {
    const originalTimeout = globalThis.setTimeout;
    let expire;
    globalThis.setTimeout = (callback) => { expire = callback; return 0; };
    try {
        const value = session();
        const ready = value.ensureLoaded();
        expire();
        await assert.rejects(ready, /startup timed out/);
        assert.equal(worker.terminated, true);
    } finally {
        globalThis.setTimeout = originalTimeout;
    }
});

test('termination rejects readiness and every in-flight request', async () => {
    const starting = session();
    const ready = starting.ensureLoaded();
    starting.terminate();
    await assert.rejects(ready, /terminated/);

    const value = session();
    const loaded = value.ensureLoaded();
    worker.receive({ type: 'ready' });
    await loaded;
    const count = value.getPageCount(1);
    const page = value.openPage(1, 0);
    value.terminate();
    await assert.rejects(count, /terminated/);
    await assert.rejects(page, /terminated/);
});

test('Blob input is sent unchanged and a postMessage failure leaves the session usable', async () => {
    const value = session();
    const loaded = value.ensureLoaded();
    worker.receive({ type: 'ready' });
    await loaded;
    const blob = new Blob(['TIFF']);
    const open = value.openBlob(blob);
    assert.equal(worker.lastMessage.blob, blob);
    assert.equal(worker.lastMessage.type, 'openBlob');
    worker.receive({ id: worker.lastMessage.id, status: 0, doc: 123 });
    assert.equal((await open).doc, 123);

    const send = worker.postMessage;
    worker.postMessage = () => { throw new Error('cannot clone'); };
    await assert.rejects(value.getPageCount(123), /cannot clone/);
    worker.postMessage = send;
    const count = value.getPageCount(123);
    worker.receive({ id: worker.lastMessage.id, pageCount: 3 });
    assert.equal((await count).pageCount, 3);
});
