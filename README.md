# TiffRenderer

An Android library for decoding and rasterizing TIFF files — including multi-page/multi-directory
ones — on top of [libtiff](http://libtiff.org/), cross-compiled with the NDK behind a thin JNI
layer.

Android ships a built-in renderer for PDF ([`PdfRenderer`][pdfrenderer-docs], backed by pdfium)
but has no equivalent for TIFF. `TiffRenderer` fills that gap. Its public API is **deliberately
modeled on `PdfRenderer`'s shape** — same method names, same lifecycle, the same `Page` /
render-mode pattern — so it should feel immediately familiar if you've used `PdfRenderer` before.

[pdfrenderer-docs]: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer

```kotlin
TiffRenderer(pfd).use { renderer ->
    for (i in 0 until renderer.pageCount) {
        renderer.openPage(i).use { page ->
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            // ... draw `bitmap`
        }
    }
}
```

## Features

- **Multi-page support.** Every TIFF directory is exposed as a `Page`, the same way `PdfRenderer`
  exposes PDF pages.
- **Familiar API.** If you know `PdfRenderer`, you already know most of this library. It diverges
  from that shape only where TIFF's reality genuinely differs — see [Divergences from
  `PdfRenderer`](#divergences-from-pdfrenderer) below.
- **Two render modes.** `RENDER_MODE_FOR_DISPLAY` (bilinear resampling, for on-screen viewing) and
  `RENDER_MODE_FOR_PRINT` (nearest-neighbor, for exact pixel reproduction).
- **Opt-in decode caching.** `Page#retainRaster()` decodes a page once and reuses that decode
  across repeated `render()` calls — useful when rendering the same page at multiple zoom levels
  or tile sizes. Off by default, since the cached raster is the page's full uncompressed pixel
  grid (hundreds of MB for a large scanned page).
- **Clip and transform support.** `render()` accepts an optional destination clip `Rect` and an
  optional affine `Matrix`, matching `PdfRenderer.Page#render`'s signature.

## Codec support

This is a young library, and codec support is intentionally narrow in this first version:

| Codec                | Supported | Notes                                    |
|-----------------------|:---------:|-------------------------------------------|
| Uncompressed          | ✅        |                                            |
| PackBits              | ✅        |                                            |
| LZW                   | ✅        |                                            |
| CCITT Group 3/4 (fax) | ✅        |                                            |
| Deflate / ZIP         | ✅        | via the NDK's bundled zlib                |
| JPEG-in-TIFF          | ❌        | would require vendoring libjpeg           |
| WebP                  | ❌        | would require vendoring libwebp           |
| Zstd                  | ❌        | would require vendoring libzstd           |
| LERC                  | ❌        | would require vendoring LERC              |
| LZMA                  | ❌        | would require vendoring liblzma           |
| JBIG                  | ❌        | untested, no fixture available            |

A TIFF using an unsupported codec opens fine (the compression tag is just metadata until
something actually tries to decode pixels) but `Page#render()` throws `IOException` once decoding
is actually attempted — it never silently misdecodes or crashes.

## Installation

Not published to Maven Central. Available via [JitPack](https://jitpack.io/) off of GitHub
release tags:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.lucf15.TiffRenderer:lib:<tag>") // see the badge above for the latest tag
}
```

Or, to build against a local clone instead, a Gradle
[composite build](https://docs.gradle.org/current/userguide/composite_builds.html):

```kotlin
// settings.gradle.kts, in the app that wants to depend on this library
includeBuild("../path/to/TiffRenderer") {
    dependencySubstitution {
        substitute(module("io.github.lucf15:tiffrenderer")).using(project(":lib"))
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.lucf15:tiffrenderer")
}
```

Building the library requires the Android NDK and CMake (versions pinned in `lib/build.gradle.kts`
via `ndkVersion` / `externalNativeBuild.cmake.version`); Gradle will fetch them automatically if
they aren't already installed. libtiff itself is vendored as a git submodule, so clone with
`--recurse-submodules` (or run `git submodule update --init --recursive` afterwards) before
building.

## Quick start

```kotlin
import io.github.lucf15.tiffrenderer.TiffRenderer

// `pfd` must be a seekable ParcelFileDescriptor -- e.g. from a content:// Uri via
// context.contentResolver.openFileDescriptor(uri, "r"). TiffRenderer takes ownership of it.
TiffRenderer(pfd).use { renderer ->
    renderer.openPage(0).use { page ->
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    }
}
```

Rendering into a sub-region of a larger bitmap, or with a custom transform:

```kotlin
page.render(
    bitmap,
    Rect(0, 0, 512, 512),          // destination clip
    Matrix().apply { postScale(2f, 2f) },
    TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY,
)
```

Rendering the same page repeatedly (e.g. multiple zoom tiles) without redecoding each time:

```kotlin
renderer.openPage(0).use { page ->
    page.retainRaster()
    // every render() call below reuses the same decode
    page.render(tile1, clip1, transform1, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    page.render(tile2, clip2, transform2, TiffRenderer.Page.RENDER_MODE_FOR_DISPLAY)
}
```

### Lifecycle

- One `TiffRenderer` per open document; close it when you're done with `close()` (or `.use {}`).
- Only one `Page` may be open at a time per `TiffRenderer`, matching libtiff's own
  single-directory-cursor model — open, render, close before opening the next page.
- `TiffRenderer(...)`, `openPage()`, and `Page#render()`/`retainRaster()` all declare
  `throws IOException`: a directory can fail to open, or a page's compression scheme can fail to
  decode, even after the document itself opened successfully.

### Divergences from `PdfRenderer`

- `openPage()` and `Page#render()` are declared `throws IOException`; `PdfRenderer`'s aren't.
  TIFF's per-directory codec independence means decoding can genuinely fail per-page even after a
  successful open, unlike a fully-parsed-up-front PDF.
- `RENDER_MODE_FOR_DISPLAY` / `RENDER_MODE_FOR_PRINT` select an actual different resampling filter
  (bilinear vs. nearest-neighbor) rather than being a decorative pass-through, since TIFF is
  always raster content (never vector).

## Requirements

- `minSdk` 24+
- Native libraries are built for `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`.

## Sample app

The `sample/` module is a small Compose app that doubles as a manual test harness: pick any TIFF
via the system file picker and page through it in an edge-to-edge scrolling viewer.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
