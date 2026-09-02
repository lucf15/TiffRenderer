# TiffRenderer

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lucf15/tiffrenderer.svg)](https://central.sonatype.com/artifact/io.github.lucf15/tiffrenderer)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS%20%7C%20JVM%20%7C%20Web-blue)](#requirements)
[![CI](https://github.com/lucf15/TiffRenderer/actions/workflows/ci.yml/badge.svg)](https://github.com/lucf15/TiffRenderer/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

A Kotlin Multiplatform library for decoding and rendering TIFF images — **Android, iOS,
JVM/desktop, and Web (Kotlin/Wasm)** — including multi-page/multi-directory TIFFs, on top of
`libtiff`.

```kotlin
// fd: an already-open, seekable file descriptor
suspend fun loadThumbnail(fd: FileDescriptor, size: Long) {
    TiffRenderer.open(TiffSource.fromFileDescriptor(fd, size)).use { renderer ->
        renderer.openPage(0).use { page ->
            val bitmap = createTiffBitmap(page.width, page.height)
            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
        }
    }
}
```

## Contents

- [Why TiffRenderer](#why-tiffrenderer)
- [Install](#install)
  - [Android](#android)
  - [iOS](#ios)
  - [JVM / desktop](#jvm--desktop)
  - [Web (Kotlin/Wasm)](#web-kotlinwasm)
  - [Building from source](#building-from-source)
- [Getting started](#getting-started)
  - [Android convenience overloads](#android-convenience-overloads)
  - [JVM / desktop](#jvm--desktop-1)
  - [Web (Kotlin/Wasm)](#web-kotlinwasm-1)
- [Lifecycle](#lifecycle)
- [Limitations](#limitations)
- [Codec support](#codec-support)
- [Testing](#testing)
- [Native libraries](#native-libraries)
- [Requirements](#requirements)
- [Sample app](#sample-app)
- [License](#license)

## Why TiffRenderer

Android has no built-in TIFF decoder. PDF gets `android.graphics.pdf.PdfRenderer`, backed by
pdfium; TIFF gets nothing. TiffRenderer fills that gap. Its public API is deliberately modeled on
`PdfRenderer`'s own shape — same method names, same lifecycle, same page/render-mode pattern — so
it's immediately familiar to any Android developer. It diverges only where the underlying reality
genuinely differs, documented inline where that happens.

`TiffRenderer`/`TiffPage` are **one implementation shared across Android, iOS, JVM, and Web** — a
single platform-neutral C++ core, with only the innermost native call swapped per platform (on Web
it runs inside a Web Worker, an Emscripten build of the same core) — so decoding and rendering
never drift between them. The whole public API is `suspend`-based so every call is safe to make
directly from `Dispatchers.Main`/`viewModelScope`, and so Web can offload decoding to that Worker
instead of blocking the browser's single UI thread.

- Every TIFF directory is a `TiffPage`. `render()` takes an optional destination clip and affine
  transform.
- `TiffPage#retainRaster()` opts a page into decode caching, so rendering the same page at
  multiple zoom levels doesn't redecode each time. Off by default — a cached raster is the page's
  full uncompressed pixel grid, hundreds of MB for a large scan.
- `TiffRenderMode.FOR_DISPLAY` (bilinear + mip minification) and `FOR_PRINT` (nearest-neighbor)
  are genuinely different resampling paths, not just labels.
- On Android, `TiffRenderer(ParcelFileDescriptor)` and
  `TiffPage#render(Bitmap, Rect?, Matrix?, TiffRenderMode)` take the platform's own types
  directly — no need to touch the cross-platform `TiffSource`/`TiffBitmap`/`TiffRect`/
  `TiffTransform` wrappers at all.

## Install

> Versions before 2.0.0 were published to JitPack under `com.github.lucf15:TiffRenderer`. That
> coordinate is no longer updated; migrate to the Maven Central one below.

Published to Maven Central as `io.github.lucf15:tiffrenderer`, a regular Kotlin Multiplatform
artifact (Android, JVM/desktop, `iosArm64`/`iosSimulatorArm64`, `wasmJs`):

```kotlin
// build.gradle.kts, in a Kotlin Multiplatform module
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.lucf15:tiffrenderer:<version>") // see the badge above for the latest
        }
    }
}
```

### Android

A plain (non-KMP) Android app can depend on it directly instead:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.lucf15:tiffrenderer:<version>")
}
```

`tiffrenderer`'s Android target pulls in `io.github.lucf15:tiffrenderer-native` (the compiled
`.so`s) transitively; nothing extra to add for that.

### iOS

Any Kotlin Multiplatform project can depend on the Maven coordinate above from its own
`iosMain`/`iosArm64`/`iosSimulatorArm64` source sets — no separate iOS-specific artifact. A pure
Swift/Xcode project with no Kotlin involved still needs a real `.framework`/XCFramework, which
this coordinate alone doesn't provide (Maven has no such artifact shape); build one from source
the way `sample/iosApp` does, via `:lib:core`'s `embedAndSignAppleFrameworkForXcode` Gradle task.

### JVM / desktop

Same coordinate, `jvm()` target — works from a plain JVM project too, not just KMP:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.lucf15:tiffrenderer:<version>")
}
```

Bundles native libraries for macOS (`aarch64`, `x86_64`), Linux (`x86_64`, `aarch64`), and Windows
(`x86_64`, `aarch64`); the right one is picked automatically at runtime based on the host JVM's
`os.name`/`os.arch`.

### Web (Kotlin/Wasm)

Same coordinate, `wasmJs()` target, from a Compose Multiplatform or plain Kotlin/Wasm project's
`wasmJsMain`:

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        wasmJsMain.dependencies {
            implementation("io.github.lucf15:tiffrenderer:<version>")
        }
    }
}
```

Decoding runs inside a dedicated Web Worker (spawned automatically on first use) rather than on
the page's main thread, so a large TIFF's decode doesn't freeze scrolling/painting while it runs.

### Building from source

To build against a local clone instead of the published artifact, a Gradle
[composite build](https://docs.gradle.org/current/userguide/composite_builds.html):

```kotlin
// settings.gradle.kts, in the app that wants to depend on this library
includeBuild("../path/to/TiffRenderer") {
    dependencySubstitution {
        substitute(module("io.github.lucf15:tiffrenderer")).using(project(":lib:core"))
    }
}
```

Building the library requires the Android NDK and CMake, versions pinned in
`gradle/libs.versions.toml`; Gradle fetches them automatically if they aren't already installed.
Building for iOS additionally requires Xcode. `:lib:core`'s iOS cinterop tasks depend on
`:lib:native`'s `buildTiffCoreForIos` task, so the native cross-compile
(`lib/native/src/main/cpp/build-ios.sh`) runs automatically as part of any Gradle iOS build — no
manual step needed.

libtiff, libjpeg, and libwebp are all vendored as git submodules. Clone with
`--recurse-submodules`, or run `git submodule update --init --recursive` afterwards, before
building any platform.

Building for `wasmJs` additionally requires [Emscripten](https://emscripten.org/) (`emcmake`) on
`PATH`; `:lib:core`'s `wasmJsProcessResources` task depends on `:lib:native`'s
`buildTiffCoreForWasm` task, so that cross-compile runs automatically as part of any Gradle wasmJs
build — no manual step needed.

## Getting started

The snippet at the top of this README is the minimal case: open a source, open a page, render it.
Every call below is a `suspend fun`, safe to call directly from `Dispatchers.Main`/
`viewModelScope`; it internally dispatches decode work to a background dispatcher (or, on Web, the
Worker) itself. A few more common cases:

Rendering into a sub-region with a custom transform:

```kotlin
page.render(
    bitmap,
    destClip = TiffRect(0, 0, 512, 512),
    transform = TiffTransform(floatArrayOf(2f, 0f, 0f, 0f, 2f, 0f)), // 2x scale
    renderMode = TiffRenderMode.FOR_DISPLAY,
)
```

Rendering the same page repeatedly (e.g. multiple zoom tiles) without redecoding each time:

```kotlin
renderer.openPage(0).use { page ->
    page.retainRaster()
    // every render() call below reuses the same decode
    page.render(tile1, clip1, transform1, TiffRenderMode.FOR_DISPLAY)
    page.render(tile2, clip2, transform2, TiffRenderMode.FOR_DISPLAY)
}
```

### Android convenience overloads

On Android, everything above can take the platform's own `ParcelFileDescriptor`/`Bitmap`/`Rect`/
`Matrix` types directly instead of the cross-platform `TiffSource`/`TiffBitmap`/`TiffRect`/
`TiffTransform` wrappers:

```kotlin
// `pfd` must be a seekable ParcelFileDescriptor, e.g. from a content:// Uri via
// context.contentResolver.openFileDescriptor(uri, "r"). TiffRenderer takes ownership of it.
suspend fun renderPage(pfd: ParcelFileDescriptor) {
    TiffRenderer(pfd).use { renderer ->
        renderer.openPage(0).use { page ->
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, Rect(0, 0, 512, 512), Matrix().apply { setScale(2f, 2f) })
        }
    }
}
```

`TiffTransform`/the `Matrix` overload only ever represent an **affine** transform (6 components);
a genuinely non-affine (perspective) `Matrix` throws `IllegalArgumentException`, since neither
type can represent one.

### JVM / desktop

```kotlin
import io.github.lucf15.tiffrenderer.TiffRenderer
import io.github.lucf15.tiffrenderer.TiffSource
import io.github.lucf15.tiffrenderer.TiffRenderMode
import io.github.lucf15.tiffrenderer.use
import java.io.File

suspend fun renderPage(file: File) {
    TiffRenderer.open(TiffSource.fromFile(file)).use { renderer ->
        renderer.openPage(0).use { page ->
            val bitmap = createTiffBitmap(page.width, page.height)
            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
            val pixels = bitmap.toIntArray() // packed ARGB, row-major
        }
    }
}
```

`TiffBitmap`'s pixels live in a direct (off-heap) `ByteBuffer`, so the native render call writes
straight into it with no JVM-heap copy. `toIntArray()` above repacks into packed ARGB ints for
convenience; `bitmap.toByteArray()` returns the raw RGBA8888 bytes directly (e.g. for handing to
Skia's `installPixels`) without that per-pixel repacking.

A direct `ByteBuffer` is off-heap memory. The JVM only reclaims it when its wrapper object is
garbage-collected — there's no deterministic `close()`/`free()` for it. `createTiffBitmap`/
`TiffBitmap(width, height)` allocate a fresh buffer every call. That's fine for a one-off render,
but for a large page rendered repeatedly (e.g. on every resize or scroll frame), new buffers can
pile up faster than GC reclaims the old ones and spike memory well past the heap's own `-Xmx`. For
that case, allocate once and reuse:

```kotlin
val buffer = java.nio.ByteBuffer.allocateDirect(width * height * 4)
val bitmap = TiffBitmap.wrapping(buffer, width, height)
page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY) // render into `buffer` again on the next call
```

`TiffBitmap.wrapping` requires a direct, writable buffer at least `width * height * 4` bytes.

### Web (Kotlin/Wasm)

```kotlin
import io.github.lucf15.tiffrenderer.TiffRenderer
import io.github.lucf15.tiffrenderer.TiffSource
import io.github.lucf15.tiffrenderer.TiffRenderMode
import io.github.lucf15.tiffrenderer.use

// bytes: the picked file's contents, e.g. from a browser File/FileReader
suspend fun renderPage(bytes: ByteArray) {
    TiffRenderer.open(TiffSource.fromByteArray(bytes)).use { renderer ->
        renderer.openPage(0).use { page ->
            val bitmap = createTiffBitmap(page.width, page.height)
            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
        }
    }
}
```

Identical shape to every other platform; the only difference is `TiffSource.fromByteArray`, since
there's no portable file descriptor or filesystem path to open from a browser. The first call in a
page's lifetime spawns the decode Worker, awaited internally, so no separate warm-up step is
needed.

## Lifecycle

- One `TiffRenderer` per open document; close it when you're done, with `close()` or `.use {}`
  (from `io.github.lucf15.tiffrenderer.use`, not `kotlin.io.use` — `close()` is `suspend`, so
  `TiffRenderer`/`TiffPage` aren't `AutoCloseable`).
- Only one `TiffPage` may be open at a time per `TiffRenderer`, matching libtiff's own
  single-directory-cursor model: open, render, close before opening the next page.
- `TiffRenderer.open(...)`, `openPage()`, and `TiffPage#render()`/`retainRaster()` can all throw
  [`TiffIOException`](lib/core/src/commonMain/kotlin/io/github/lucf15/tiffrenderer/TiffIOException.kt)
  (an unchecked `RuntimeException`): a directory can fail to open, or a page's compression scheme
  can fail to decode, even after the document itself opened successfully — TIFF's per-directory
  codec independence means decode failures are genuinely per-page, not just per-file.

## Limitations

- **Not thread safe.** A `TiffRenderer`/`TiffPage` instance must not be called concurrently from
  more than one thread; a caller doing so (e.g. a scrolling UI rendering several pages at once)
  needs its own external lock around calls into the same instance.
- **Full page decoded into memory, always.** `render()` decodes the entire source page into an
  uncompressed RGBA raster before drawing any of it, even into a small destination bitmap — there's
  no tiled or streaming decode. Use `destClip`/`transform` to control the *destination* size, not
  to reduce how much of the source gets decoded.
- **Decode is capped, not unbounded.** Pages beyond ~250 million pixels (64-bit targets) or ~64
  million pixels (32-bit Android ABIs — `armeabi-v7a`/`x86`) throw `TiffIOException` rather than
  attempting the decode, to fail cleanly instead of risking an OOM kill.
- **`TiffSource` is single-use.** Each `TiffSource` is consumed by the `TiffRenderer` it's passed
  to, even if construction fails; passing the same instance to a second `TiffRenderer` throws
  `IllegalStateException`. If a source is created but never handed to a `TiffRenderer` at all, call
  `release()` on it directly to free its underlying resource (e.g. a file descriptor) instead of
  leaving that to happen whenever the object is garbage-collected.
- **No color management, no >8-bit precision.** Decoding goes through libtiff's
  `TIFFReadRGBAImageOriented`, which flattens 16-bit-per-channel and floating-point TIFFs to plain
  8-bit RGBA and ignores any embedded ICC profile. Scientific/medical TIFFs relying on higher
  precision, or documents expecting color-managed rendering, decode without error but lose that
  information silently.

## Codec support

Codec support is intentionally narrow in this first version:

| Codec                  | Supported | Notes                                                             |
|------------------------|:---------:|--------------------------------------------------------------------|
| Uncompressed           | ✅        |                                                                    |
| PackBits               | ✅        |                                                                    |
| LZW                    | ✅        |                                                                    |
| CCITT Group 3/4 (fax)  | ✅        |                                                                    |
| Deflate / ZIP          | ✅        | via the platform's bundled zlib (Web via Emscripten's zlib port)  |
| JPEG-in-TIFF           | ✅        | via vendored IJG libjpeg                                          |
| WebP                   | ✅        | via vendored libwebp                                              |
| Zstd                   | ❌        | would require vendoring libzstd                                  |
| LERC                   | ❌        | would require vendoring LERC                                     |
| LZMA                   | ❌        | would require vendoring liblzma                                  |
| JBIG                   | ❌        | untested, no fixture available                                   |

A TIFF using an unsupported codec opens fine (the compression tag is just metadata until
something actually tries to decode pixels) but `TiffPage#render()` throws `TiffIOException` once
decoding is actually attempted. It never silently misdecodes or crashes.

## Testing

Most of the suite runs unmodified against the real native decode path on Android
(on-device/emulator), iOS (simulator), and JVM — no mocks. It covers the lifecycle state machine,
the codec support matrix above (every unsupported codec must throw `TiffIOException` from
`render()`, never `openPage()`), `retainRaster()`'s cache correctness, hostile/corrupt input
handling, and pixel-level render correctness including mip-pyramid minification. A smaller set of
platform-specific tests cover Android's `Bitmap`/`Matrix`/`Rect` convenience overloads and
JVM-specific concerns like native-library resolution and thread contention on the per-document
lock. Fixtures are real `.tif` files under `lib/core/src/commonTest/resources/`, generated by
`lib/core/src/commonTest/tools/generate_fixtures.py`.

```
./gradlew :lib:core:jvmTest                     # JVM, no device/emulator needed
./gradlew :lib:core:iosSimulatorArm64Test        # iOS simulator
./gradlew :lib:core:connectedAndroidDeviceTest   # Android, needs a running device/emulator
```

## Native libraries

Vendored as pinned git submodules under `lib/native/src/main/cpp/third_party/` (the same sources
back the Android, iOS, JVM, and Web builds):

| Library                                             | Version |
|------------------------------------------------------|---------|
| [libtiff](https://gitlab.com/libtiff/libtiff)         | 4.7.2   |
| [IJG libjpeg](https://github.com/libjpeg-turbo/ijg)   | 10      |
| [libwebp](https://github.com/webmproject/libwebp)     | 1.6.0   |

All three bump automatically, including this table (see `.github/workflows/libtiff-update-check.yml`,
`libwebp-update-check.yml`, `libjpeg-update-check.yml`).

Each library's copyright notice and license terms are reproduced in
[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).

## Requirements

| Target      | Platform  | Architectures                                | Notes                                                          |
|-------------|-----------|-----------------------------------------------|-----------------------------------------------------------------|
| Android     | Android   | `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`   | `minSdk` 24+, `compileSdk` 37                                  |
| iOS         | Device    | `arm64` (`iosArm64`)                          | Deployment target 13.0+                                        |
| iOS         | Simulator | `arm64` (`iosSimulatorArm64`)                 | No `iosX64`: Apple/Xcode dropped Intel simulator support       |
| JVM/desktop | macOS     | `aarch64`, `x86_64`                           | JDK 17+                                                        |
| JVM/desktop | Linux     | `x86_64`, `aarch64`                           | JDK 17+                                                        |
| JVM/desktop | Windows   | `x86_64`, `aarch64`                           | JDK 17+                                                        |
| Web         | Browser   | `wasm32` (`wasmJs`)                           | Needs a browser with `WebAssembly.Memory`/Web Worker support   |

## Sample app

`sample/` is a small Compose Multiplatform app that doubles as a manual test harness for every
target: pick any TIFF via the system file picker (SAF on Android, `UIDocumentPickerViewController`
on iOS, `JFileChooser` on desktop, the browser's own file input on Web) and page through it in an
edge-to-edge scrolling viewer. `sample/androidApp`, `sample/iosApp`, `sample/desktopApp`, and
`sample/webApp` are the four platform shells; `sample/shared` is the actual UI, shared between all
of them. Run the iOS app by opening `sample/iosApp/iosApp.xcodeproj` in Xcode and hitting Run; run
the desktop app via `./gradlew :sample:desktopApp:run`; run the web app via
`./gradlew :sample:webApp:wasmJsBrowserDevelopmentRun`, which opens a local dev server (typically
`http://localhost:8080`).

## License

Apache License 2.0. See [LICENSE](LICENSE).
