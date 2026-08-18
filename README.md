# TiffRenderer

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lucf15/tiffrenderer.svg)](https://central.sonatype.com/artifact/io.github.lucf15/tiffrenderer)

A Kotlin Multiplatform library for decoding and rendering TIFF images — **Android, iOS, and
JVM/desktop** — including multi-page/multi-directory TIFFs. Built on top of
[libtiff](http://libtiff.org/) (with JPEG-in-TIFF and WebP support via vendored
[IJG libjpeg](https://www.ijg.org/) and [libwebp](https://github.com/webmproject/libwebp)),
cross-compiled per platform behind one shared, platform-neutral C++ decode/resample core (Android
NDK/JNI, Xcode/Kotlin-Native cinterop on iOS, plain JNI on JVM desktop), so decode and render
behavior is identical across all three. See [Native libraries](#native-libraries) below for exact
pinned versions.

```kotlin
// Common API, identical on every platform. `fd`/`size` describe an already-open,
// already-owned, seekable file descriptor.
TiffRenderer(TiffSource.fromFileDescriptor(fd, size)).use { renderer ->
    for (i in 0 until renderer.pageCount) {
        renderer.openPage(i).use { page ->
            val bitmap = createTiffBitmap(page.width, page.height)
            page.render(bitmap, renderMode = TiffRenderMode.FOR_DISPLAY)
            // Android: bitmap.asAndroidBitmap(); iOS/JVM: bitmap.toIntArray() or your own bridge
        }
    }
}
```

## Features

- **Multi-page support.** Every TIFF directory is exposed as a `TiffPage`.
- **One implementation, three targets.** `TiffRenderer`/`TiffPage` are a single concrete
  implementation shared across Android, iOS, and JVM; only the innermost native call is
  platform-specific. Behavior doesn't drift between platforms.
- **Two render modes.** `TiffRenderMode.FOR_DISPLAY` (bilinear resampling, for on-screen viewing)
  and `FOR_PRINT` (nearest-neighbor, for exact pixel reproduction).
- **Opt-in decode caching.** `TiffPage#retainRaster()` decodes a page once and reuses that decode
  across repeated `render()` calls — useful when rendering the same page at multiple zoom levels
  or tile sizes. Off by default, since the cached raster is the page's full uncompressed pixel
  grid (hundreds of MB for a large scanned page).
- **Clip and transform support.** `render()` accepts an optional destination clip and an optional
  affine transform.
- **Android-native convenience overloads.** `TiffRenderer(ParcelFileDescriptor)` and
  `TiffPage#render(Bitmap, Rect?, Matrix?, TiffRenderMode)` accept Android's own platform types
  directly, so Android-only call sites never have to touch the cross-platform
  `TiffSource`/`TiffBitmap`/`TiffRect`/`TiffTransform` wrapper types at all.

## Installation

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

Bundles native libraries for macOS (`aarch64`), Linux (`x86_64`), and Windows (`x86_64`); the
right one is picked automatically at runtime based on the host JVM's `os.name`/`os.arch`.

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
`lib/native/build.gradle.kts` via `ndkVersion` / `externalNativeBuild.cmake.version`); Gradle will
fetch them automatically if they aren't already installed. Building for iOS additionally requires
Xcode: `:lib:core`'s iOS cinterop tasks depend on `:lib:native`'s `buildTiffCoreForIos` task, so the
native cross-compile (`lib/native/src/main/cpp/build-ios.sh`) runs automatically as part of any
Gradle iOS build — no manual step needed. libtiff, libjpeg, and libwebp are all vendored as git
submodules, so clone with `--recurse-submodules` (or run `git submodule update --init --recursive`
afterwards) before building any platform.

## Quick start

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

## Requirements

- **Android**: `minSdk` 24+. Native libraries are built for `arm64-v8a`, `armeabi-v7a`, `x86_64`,
  and `x86`.
- **iOS**: deployment target 13.0+, `iosArm64` and `iosSimulatorArm64` (no `iosX64`: Apple/Xcode
  itself has dropped support for the Intel simulator).
- **JVM/desktop**: JDK 17+; macOS (`aarch64`), Linux (`x86_64`), Windows (`x86_64`).

## Sample app

`sample/` is a small Compose Multiplatform app that doubles as a manual test harness for every
target: pick any TIFF via the system file picker (SAF on Android, `UIDocumentPickerViewController`
on iOS, `JFileChooser` on desktop) and page through it in an edge-to-edge scrolling viewer.
`sample/androidApp`, `sample/iosApp`, and `sample/desktopApp` are the three platform shells;
`sample/shared` is the actual UI, shared between them. Run the iOS app on a simulator via
`sample/run-ios-simulator.sh`; run the desktop app via `./gradlew :sample:desktopApp:run`.

## License

Apache License 2.0. See [LICENSE](LICENSE).
