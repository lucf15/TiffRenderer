@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.lucf15.tiffrenderer.sample.shared.platform

import androidx.compose.runtime.Composable
import io.github.lucf15.tiffrenderer.TiffSource
import kotlin.js.Promise
import org.w3c.files.Blob

@JsFun("""() => new Promise(resolve => {
    const input = document.createElement('input');
    input.type = 'file'; input.accept = '.tif,.tiff'; input.style.display = 'none';
    document.body.appendChild(input);
    const finish = file => { input.remove(); resolve(file); };
    input.addEventListener('change', () => finish(input.files?.[0] ?? null), {once:true});
    input.addEventListener('cancel', () => finish(null), {once:true});
    input.click();
})""")
private external fun pickFile(): Promise<Blob?>
private fun fileName(file: Blob): String = js("file.name || 'Document.tiff'")

@Composable
actual fun rememberTiffPicker(onPicked: (PickedTiff) -> Unit): () -> Unit = {
    pickFile().then<JsAny?>(
        onFulfilled = { file -> if (file != null) onPicked(PickedTiff(fileName(file), TiffSource.fromBlob(file))); null },
        onRejected = { null },
    )
}
