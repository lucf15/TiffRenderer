# TiffRenderer

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lucf15/tiffrenderer.svg)](https://central.sonatype.com/artifact/io.github.lucf15/tiffrenderer)
[![CI](https://github.com/lucf15/TiffRenderer/actions/workflows/ci.yml/badge.svg)](https://github.com/lucf15/TiffRenderer/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

**Render TIFF documents from Kotlin on Android, iOS, desktop, and the web.**

Open a document, choose a page and a size, get an image. TiffRenderer handles decoding,
concurrent requests, native resources and memory limits using one shared libtiff core.

> This checkout contains the upcoming **3.0 API**, which breaks compatibility with 2.x.
> Until 3.0 is published, build this checkout from source to use the examples below.

[Get started](#get-started) · [Platforms](#platforms) · [Rendering](#rendering) ·
[Resources and cancellation](#resources-and-cancellation) · [Limits](#limits) ·
[Sample app](#sample-app) · [Building and testing](#building-and-testing)

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/sample-dark.png">
  <img src="docs/images/sample.png" alt="The Compose Unstyled sample displaying a multipage TIFF">
</picture>

## Get started

The library is published to Maven Central:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("io.github.lucf15:tiffrenderer:<version>")
    }
}
```

Plain JVM and Android projects can add the same dependency in their `dependencies` block.
Native libraries are included automatically. Choose a release matching the API you use.

The main operation fits a page inside a bounding box while keeping its proportions:

```kotlin
val image = TiffRenderer.withDocument(source) { document ->
    document.renderPage(
        pageIndex = 0,
        size = RenderSize.fit(maxWidth = 1200, maxHeight = 1600),
    ).image
}
```

Page indices start at **0**. Sizes are in **pixels**. The document closes when the block
finishes, including after failure or cancellation. The returned image remains valid.

For a single render:

```kotlin
val result = TiffRenderer.render(source, pageIndex = 0, size = RenderSize.fit(1200, 1600))
```

## Platforms

Use the platform's file type directly, or create a reusable `TiffSource` for shared code.
Each open creates an independent document and acquires its own resources.

### Android

Open a URI returned by the system file picker:

```kotlin
val document = TiffRenderer.open(context, uri)
try {
    val result = document.renderPage(0, RenderSize.fit(1200, 1600))
    imageView.setImageBitmap(result.image.asAndroidBitmap())
} finally {
    document.close()
}
```

Or open and close automatically:

```kotlin
val bitmap = TiffRenderer.renderBitmap(context, uri, pageIndex = 0, maxWidth = 1200)
```

`open(file)` and `open(parcelFileDescriptor)` are also available. Descriptor inputs are
duplicated: the caller keeps ownership of the original, and rendering uses an independent
read position. Keep the original descriptor open until `open()` returns.

### JVM / desktop

```kotlin
TiffRenderer.withDocument(TiffSource.fromPath(path)) { document ->
    val image = document.renderPage(0, RenderSize.fit(1200, 1600)).image
    val bufferedImage = image.toBufferedImage()
}
```

`Path`, `File`, and path strings are supported. Native libraries cover macOS, Linux and
Windows on x86-64 and ARM64.

Java and synchronous JVM applications can use `BlockingTiffRenderer` with try-with-resources:

```java
try (var document = BlockingTiffRenderer.open(path)) {
    BufferedImage image = document.renderImage(0, 1200);
}
```

Its methods block the calling thread; use it outside UI threads. `TiffFutures.render(...)`
returns a `CompletableFuture`; cancelling the future cancels its coroutine request.

### iOS / Kotlin Multiplatform

```kotlin
TiffRenderer.withDocument(TiffSource.fromUrl(url)) { document ->
    val image = document.renderPage(0, RenderSize.fit(1200, 1600)).image
    val uiImage = image.toUIImage()
}
```

File URLs retain security-scoped access for the lifetime of the opened document when needed.
The library supports iOS 15+ on ARM64 devices and simulators.

For Swift, build the local package with `scripts/build-swift-package.sh`, then add `lib/swift`
as a local Swift package in Xcode:

```swift
import TiffRenderer

let image = try await TiffRenderer.render(
    url: url,
    pageIndex: 0,
    maxWidth: 1200,
    maxHeight: 1600
)
```

The Swift adapter maps task cancellation to the Kotlin request and closes its document
before completing. The package build requires macOS and Xcode.

### Web / Kotlin/Wasm

Prefer the browser `File` or `Blob` directly:

```kotlin
TiffRenderer.withDocument(TiffSource.fromBlob(file)) { document ->
    val result = document.renderPage(0, RenderSize.fit(1200, 1600))
}
```

Each document runs in its own worker. Files are read there, so large inputs do not pass through
base64 strings or Kotlin byte arrays on the UI thread. Closing the document terminates its
worker and releases its Wasm heap.

Rendered pixels remain in a JavaScript `ArrayBuffer`. The sample's Compose bridge uses
Skiko's `installPixelsFromArrayBuffer` to display them without an intermediate Kotlin copy.
The browser must support Kotlin/Wasm, workers, and the typed-array base64 APIs used by explicit
Kotlin byte-array import/export.

### In-memory input

Available on every platform:

```kotlin
val source = TiffSource.fromByteArray(bytes)
```

This takes an independent snapshot. Prefer file/URI/URL/Blob input for large documents to
avoid the extra copy. The source is reusable and has no `close()` or `release()` obligation.

## Rendering

| Operation | Use it for |
|---|---|
| `pageCount()` | Number of pages; resolved lazily by walking the TIFF directories |
| `pageInfo(index)` | Page index, original width, height and raster size estimate |
| `renderPage(index, size)` | A new, independent output image |
| `renderInto(index, destination, ...)` | Reusing a destination with a clip or affine transform |
| `trimCache()` | Releasing the document's decoded raster cache |

`RenderSize.fit(...)` preserves aspect ratio and avoids upscaling by default. Set
`allowUpscale = true` explicitly, or choose `RenderSize.Original` for native resolution.

`TiffSampling.SMOOTH` uses bilinear sampling with mip minification. `NEAREST` preserves discrete
source pixels. `TiffTransform.scale`, `translate`, `rotate`, and `identity` build affine transforms.

Advanced in-place rendering accepts `TiffRect` and `TiffTransform`; Android also accepts native
`Bitmap`, `Rect` and `Matrix` values. Pixels outside the clip or transformed page are preserved.
An in-place operation can modify the destination before reporting a decode error.

### Damaged files

Strict decoding is the default. A partial decode throws `TiffIOException` instead of silently
returning an incomplete page. To accept recoverable damage explicitly:

```kotlin
val options = TiffOptions(decodePolicy = TiffDecodePolicy.ALLOW_PARTIAL)
val result = TiffRenderer.render(source, 0, RenderSize.fit(1200, 1600), options)
if (TiffWarning.PARTIAL_DECODE in result.warnings) {
    // Tell the user that the page is incomplete.
}
```

`TiffIOException.reason` distinguishes resource limits, partial decoding, unavailable input,
worker failures and decode errors. Invalid arguments and use after close remain programming errors.

## Resources and cancellation

| Resource | Ownership |
|---|---|
| Input description | Reusable; owns no open file descriptor |
| Open document | Owns its acquired input, native decoder, worker and raster cache |
| Rendered image | Independent of the document; follows platform GC/ARC lifetime |
| Borrowed pixel view | Valid according to its platform contract; never transfer/detach its backing storage |

Operations on one document are serialized internally. Callers can request different pages from
concurrent coroutines without a separate mutex. `close()` waits for exclusive access, releases
resources even from a cancelled coroutine, and is safe to call repeatedly.

`open`, metadata reads, rendering, cache release and close are suspending operations. Reading
already returned page properties and constructing render options are synchronous. Pixel exports
such as `copyPixels()` and conversions such as `toBufferedImage()` allocate/copy memory.

Cancellation stops waiting and prevents stale results from being published. A native libtiff call
already in progress may finish before cleanup can complete. Suspending does not imply immediate
interruption of native decoding.

## Limits

The decoder always reads the **full source page** before resampling. A small thumbnail reduces
output memory, not the source raster's decoding cost.

The defaults are conservative and configurable per document with `TiffOptions(limits = ...)`:

| Limit | Default |
|---|---:|
| Input file | 128 MiB |
| Decoded source page | 128 MiB |
| Output image | 64 MiB |
| Native working budget | 256 MiB |
| Retained raster cache | Off |

Use `TiffOptions(cacheBytes = 16L * 1024 * 1024)` to keep a small decoded page for repeated
renders. The working budget accounts for native raster, mip, cache and destination costs,
with a 32 MiB reserve for libtiff. It is not a process-wide RSS cap: platform images,
allocator capacity and concurrent documents have additional costs. libtiff allocations
have a separate 32 MiB cumulative cap.

The native core also enforces hard upper bounds. Decode/working/output limits are checked before
large rendering allocations. In-memory inputs require extra staging space. A cache only retains
a page that fits `cacheBytes`, is released on document close, and can be trimmed explicitly.

Color output is 8-bit RGBA without ICC color management. High-bit-depth and floating-point TIFFs
lose precision when converted for display.

Supported compression: **uncompressed, PackBits, LZW, CCITT Group 3/4, Deflate, JPEG and WebP**.
Zstd, LERC, LZMA, JBIG and legacy JPEG are outside the supported codec set.

## Sample app

The sample is a Compose Unstyled document workspace for Android, iOS, desktop and Wasm.
It opens with an original four-page TIFF publication. The document viewer includes a resizable
thumbnail sidebar, grouped toolbar controls, fit-width and fit-page modes, light and dark appearances,
keyboard shortcuts and native file pickers. Additional bundled examples are available after closing
the current document from the information panel.

```shell
./gradlew :sample:desktopApp:run
./gradlew :sample:webApp:wasmJsBrowserDevelopmentRun
./gradlew :sample:androidApp:assembleDebug
```

Open `sample/iosApp/iosApp.xcodeproj` to run on iOS. On the web, the dev server normally listens
on [localhost:8080](http://localhost:8080).

Generate optional local test documents with:

```shell
python3 sample/tiff-examples/generate_samples.py
```

Requires Pillow and the fonts selected by the script. It produces small text documents,
multipage faxes, noisy color scans and a larger uncompressed TIFF. Generated test binaries are ignored
by Git. To regenerate the bundled publication, run
`python3 sample/tiff-examples/generate_catalog.py` with Pillow installed; it uses the bundled Inter fonts.

## Building and testing

Initialize native dependencies after cloning:

```shell
git submodule update --init --recursive
```

Use the Gradle wrapper and the toolchain versions in `gradle/libs.versions.toml`. Native builds
require the Android SDK/NDK and CMake; iOS additionally requires Xcode, and Wasm requires Emscripten.

```shell
./gradlew :lib:core:jvmTest
./gradlew :lib:core:wasmJsTest
./gradlew :lib:core:iosSimulatorArm64Test
./gradlew :lib:core:connectedAndroidDeviceTest
./gradlew :sample:webApp:wasmJsTest
node --test lib/native/src/test/js/tiffcore-glue.test.mjs
```

Browser tests require Chrome/Chromium. Android tests require a device/emulator; iOS tests require
a compatible simulator. CI runs real decode fixtures, worker lifecycle tests, native sanitizer
checks and scheduled fuzzing. Fuzz crashes in dependencies fail the job too.

The C++ core uses libtiff, IJG libjpeg and libwebp. See [third-party licenses](THIRD_PARTY_LICENSES.md).

## License

[Apache 2.0](LICENSE).
