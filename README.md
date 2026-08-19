# TiffRenderer

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lucf15/tiffrenderer.svg)](https://central.sonatype.com/artifact/io.github.lucf15/tiffrenderer)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS%20%7C%20JVM-blue)](#requirements)
[![CI](https://github.com/lucf15/TiffRenderer/actions/workflows/ci.yml/badge.svg)](https://github.com/lucf15/TiffRenderer/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

A Kotlin Multiplatform library for decoding and rendering TIFF images — **Android, iOS, and
JVM/desktop** — including multi-page/multi-directory TIFFs, on top of `libtiff`.

```kotlin
val toolkit = TiffRenderer(TiffSource.fromFileDescriptor(fd, size)) // fd: an already-open, seekable file descriptor

toolkit.use { renderer ->
    renderer.openPage(0).use { page ->
        val bitmap = createTiffBitmap(page.width, page.height)
        page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
    }
}
```

## Contents

- [Why TiffRenderer](#why-tiffrenderer)
- [Install](#install)
  - [Android](#android)
  - [iOS](#ios)
  - [JVM / desktop](#jvm--desktop)
  - [Building from source](#building-from-source)
- [Getting started](#getting-started)
  - [Android convenience overloads](#android-convenience-overloads)
  - [JVM / desktop](#jvm--desktop-1)
- [Lifecycle](#lifecycle)
- [Codec support](#codec-support)
- [Testing](#testing)
- [Native libraries](#native-libraries)
- [Requirements](#requirements)
- [Sample app](#sample-app)
- [License](#license)

## Why TiffRenderer

Android has no built-in TIFF decoder — unlike PDF, which gets `android.graphics.pdf.PdfRenderer`
backed by pdfium, there's simply no equivalent for TIFF. TiffRenderer fills that gap, and its
public API is deliberately modeled on `PdfRenderer`'s own shape (same method names, same
lifecycle, same page/render-mode pattern), so it's immediately familiar to any Android developer,
with divergences only where the underlying reality genuinely differs (documented inline where they
occur).

`TiffRenderer`/`TiffPage` are a **single concrete implementation shared across Android, iOS, and
JVM**, cross-compiled behind one platform-neutral C++ decode/resample core (Android NDK/JNI,
Xcode/Kotlin-Native cinterop on iOS, plain JNI on JVM desktop); only the innermost native call is
platform-specific, so decode and render behavior doesn't drift between platforms. Every TIFF
directory is exposed as a `TiffPage`, `render()` accepts an optional destination clip and affine
transform, and `TiffPage#retainRaster()` gives you opt-in decode caching for rendering the same
page at multiple zoom levels or tile sizes without redecoding each time (off by default, since a
cached raster is the page's full uncompressed pixel grid — hundreds of MB for a large scanned
page). `TiffRenderMode.FOR_DISPLAY` (bilinear + mip-level minification, for on-screen viewing) and
`FOR_PRINT` (nearest-neighbor, for exact pixel reproduction) select genuinely different resampling
behavior, not a decorative pass-through enum. On Android specifically, `TiffRenderer(ParcelFileDescriptor)`
and `TiffPage#render(Bitmap, Rect?, Matrix?, TiffRenderMode)` accept the platform's own types
directly, so Android-only call sites never have to touch the cross-platform
`TiffSource`/`TiffBitmap`/`TiffRect`/`TiffTransform` wrapper types at all. JPEG-in-TIFF and WebP
are supported via vendored [IJG libjpeg](https://www.ijg.org/) and
[libwebp](https://github.com/webmproject/libwebp); see [Native libraries](#native-libraries) below
for exact pinned versions.

## Install

> Versions before 2.0.0 were published to JitPack under `com.github.lucf15:TiffRenderer`. That
> coordinate is no longer updated; migrate to the Maven Central one below.

Published to Maven Central as `io.github.lucf15:tiffrenderer`, a regular Kotlin Multiplatform
artifact (Android, JVM/desktop, `iosArm64`/`iosSimulatorArm64`):

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

Building the library requires the Android NDK and CMake (versions pinned in
`gradle/libs.versions.toml`); Gradle will fetch them automatically if they aren't already
installed. Building for iOS additionally requires
Xcode: `:lib:core`'s iOS cinterop tasks depend on `:lib:native`'s `buildTiffCoreForIos` task, so the
native cross-compile (`lib/native/src/main/cpp/build-ios.sh`) runs automatically as part of any
Gradle iOS build — no manual step needed. libtiff, libjpeg, and libwebp are all vendored as git
submodules, so clone with `--recurse-submodules` (or run `git submodule update --init --recursive`
afterwards) before building any platform.

## Getting started

```kotlin
import io.github.lucf15.tiffrenderer.TiffRenderer
import io.github.lucf15.tiffrenderer.TiffRenderMode

TiffRenderer(TiffSource.fromFileDescriptor(fd, size)).use { renderer ->
    renderer.openPage(0).use { page ->
        val bitmap = createTiffBitmap(page.width, page.height)
        page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
    }
}
```

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
TiffRenderer(pfd).use { renderer ->
    renderer.openPage(0).use { page ->
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, Rect(0, 0, 512, 512), Matrix().apply { setScale(2f, 2f) })
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
import java.io.File

TiffRenderer(TiffSource.fromFile(File("/path/to/file.tif"))).use { renderer ->
    renderer.openPage(0).use { page ->
        val bitmap = createTiffBitmap(page.width, page.height)
        page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
        val pixels = bitmap.toIntArray() // packed ARGB, row-major
    }
}
```

## Lifecycle

- One `TiffRenderer` per open document; close it when you're done, with `close()` or `.use {}`.
- Only one `TiffPage` may be open at a time per `TiffRenderer`, matching libtiff's own
  single-directory-cursor model: open, render, close before opening the next page.
- `TiffRenderer(...)`, `openPage()`, and `TiffPage#render()`/`retainRaster()` can all throw
  [`TiffIOException`](lib/core/src/commonMain/kotlin/io/github/lucf15/tiffrenderer/TiffIOException.kt)
  (an unchecked `RuntimeException`): a directory can fail to open, or a page's compression scheme
  can fail to decode, even after the document itself opened successfully — TIFF's per-directory
  codec independence means decode failures are genuinely per-page, not just per-file.

## Codec support

This is a young library, and codec support is intentionally narrow in this first version:

| Codec                | Supported | Notes                                    |
|-----------------------|:---------:|-------------------------------------------|
| Uncompressed          | ✅        |                                            |
| PackBits              | ✅        |                                            |
| LZW                   | ✅        |                                            |
| CCITT Group 3/4 (fax) | ✅        |                                            |
| Deflate / ZIP         | ✅        | via the platform's bundled zlib            |
| JPEG-in-TIFF          | ✅        | via vendored IJG libjpeg                  |
| WebP                  | ✅        | via vendored libwebp                      |
| Zstd                  | ❌        | would require vendoring libzstd           |
| LERC                  | ❌        | would require vendoring LERC              |
| LZMA                  | ❌        | would require vendoring liblzma           |
| JBIG                  | ❌        | untested, no fixture available            |

A TIFF using an unsupported codec opens fine (the compression tag is just metadata until
something actually tries to decode pixels) but `TiffPage#render()` throws `TiffIOException` once
decoding is actually attempted. It never silently misdecodes or crashes.

## Testing

The bulk of the suite lives in one shared `integrationTest` source set and runs, unmodified,
against the real native decode path on all three targets — Android (on-device/emulator), iOS
(simulator), and JVM — not mocked or platform-specific:

- **`TiffRendererLifecycleTest`** — construction, open/close state machine, the
  one-page-open-at-a-time invariant.
- **`TiffRendererCodecTest`** — the codec support matrix above: every supported codec must decode,
  every unsupported one must throw `TiffIOException` specifically from `render()`, never from
  `openPage()`.
- **`TiffRendererRetainRasterTest`** — `retainRaster()`'s repeated-render consistency and cache
  invalidation across pages.
- **`TiffRendererCorruptInputTest`** — hostile/corrupt TIFFs (adversarial dimensions, truncated
  data) must surface as `TiffIOException`, never a crash.
- **`TiffRendererRenderTest`** — pixel-level render correctness (default fit-to-clip transform,
  explicit clip, custom transform).
- **`TiffRendererMinificationTest`** — downscaling a fine checkerboard must blend across the mip
  pyramid instead of aliasing to stark black/white.

Fixtures are real `.tif` files under `lib/core/src/commonTest/resources/`, generated by
`lib/core/src/commonTest/tools/generate_fixtures.py`. A handful of tests are genuinely
platform-specific and live outside `integrationTest`: `TiffRendererAndroidNativeOverloadTest`
covers the `Bitmap`/`Matrix`/`Rect` convenience overloads (Android only, since those types don't
exist elsewhere), and `TiffSourceByteArrayTest`/`TiffBitmapOverflowTest` cover JVM/iOS-specific
edge cases.

```
./gradlew :lib:core:jvmTest                     # JVM, no device/emulator needed
./gradlew :lib:core:iosSimulatorArm64Test        # iOS simulator
./gradlew :lib:core:connectedAndroidDeviceTest   # Android, needs a running device/emulator
```

## Native libraries

Vendored as pinned git submodules under `lib/native/src/main/cpp/third_party/` (the same sources
back the Android, iOS, and JVM builds):

| Library                                              | Version |
|-------------------------------------------------------|---------|
| [libtiff](https://gitlab.com/libtiff/libtiff)          | 4.7.2   |
| [IJG libjpeg](https://github.com/libjpeg-turbo/ijg)    | 10      |
| [libwebp](https://github.com/webmproject/libwebp)      | 1.6.0   |

libtiff and libwebp both bump automatically, including this table (see
`.github/workflows/libtiff-update-check.yml` / `libwebp-update-check.yml`). libjpeg's release
cadence is measured in years rather than months, so its version is bumped by hand instead.

Each library's copyright notice and license terms are reproduced in
[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).

## Requirements

| Target      | Platform  | Architectures                                | Notes                                                     |
| ----------- | --------- | --------------------------------------------- | ---------------------------------------------------------- |
| Android     | Android   | `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`   | `minSdk` 24+, `compileSdk` 37                               |
| iOS         | Device    | `arm64` (`iosArm64`)                          | Deployment target 13.0+                                     |
| iOS         | Simulator | `arm64` (`iosSimulatorArm64`)                 | No `iosX64`: Apple/Xcode dropped Intel simulator support    |
| JVM/desktop | macOS     | `aarch64`, `x86_64`                           | JDK 17+                                                     |
| JVM/desktop | Linux     | `x86_64`, `aarch64`                           | JDK 17+                                                     |
| JVM/desktop | Windows   | `x86_64`, `aarch64`                           | JDK 17+                                                     |

## Sample app

`sample/` is a small Compose Multiplatform app that doubles as a manual test harness for every
target: pick any TIFF via the system file picker (SAF on Android, `UIDocumentPickerViewController`
on iOS, `JFileChooser` on desktop) and page through it in an edge-to-edge scrolling viewer.
`sample/androidApp`, `sample/iosApp`, and `sample/desktopApp` are the three platform shells;
`sample/shared` is the actual UI, shared between them. Run the iOS app on a simulator via
`sample/run-ios-simulator.sh`; run the desktop app via `./gradlew :sample:desktopApp:run`.

## License

Apache License 2.0. See [LICENSE](LICENSE).
