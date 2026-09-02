@file:OptIn(ExperimentalWasmJsInterop::class, kotlin.io.encoding.ExperimentalEncodingApi::class)

package io.github.lucf15.tiffrenderer.sample.shared.platform

import androidx.compose.runtime.Composable
import io.github.lucf15.tiffrenderer.TiffSource
import kotlin.io.encoding.Base64
import kotlin.js.Promise

private val pickedBytes = mutableMapOf<String, ByteArray>()
private var nextId = 0

// FileReader.readAsDataURL, not file.arrayBuffer(): Kotlin/Wasm has no fast ByteArray bridge, so
// pulling a multi-MB file out one JS call per byte would take minutes.
@JsFun(
    """
() => new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.tif,.tiff';
    input.style.display = 'none';
    document.body.appendChild(input);
    input.addEventListener('change', () => {
        const file = input.files && input.files.length > 0 ? input.files[0] : null;
        document.body.removeChild(input);
        if (!file) { resolve(null); return; }
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result.slice(reader.result.indexOf(',') + 1));
        reader.onerror = () => resolve(null);
        reader.readAsDataURL(file);
    });
    input.click();
})
""",
)
private external fun pickFileBase64(): Promise<JsString?>

@Composable
actual fun rememberTiffFilePickerLauncher(onPicked: (String) -> Unit): () -> Unit = {
    pickFileBase64().then<JsAny?>(
        onFulfilled = { result ->
            if (result != null) {
                val bytes = Base64.decode(result.toString())
                // Only one pick is ever in flight at a time in this sample's UI, so a previous
                // pick that was never opened (the user picked again, or navigated away) would
                // otherwise sit in the map forever; clearing here bounds it to at most one entry.
                pickedBytes.clear()
                val id = "wasm-file-${nextId++}"
                pickedBytes[id] = bytes
                onPicked(id)
            }
            null
        },
        onRejected = { null },
    )
}

@Composable
actual fun rememberTiffSourceOpener(): (String) -> TiffSource = { id ->
    val bytes = checkNotNull(pickedBytes.remove(id)) { "no picked bytes for $id" }
    TiffSource.fromByteArray(bytes)
}
