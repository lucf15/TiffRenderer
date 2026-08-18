#!/usr/bin/env bash
set -euo pipefail

# Cross-compiles tiffrenderer_core (vendored libtiff/libjpeg/libwebp + tiff_core.cpp/tiff_io.cpp,
# no JNI) as a static library for each iOS Kotlin/Native target: same CMakeLists.txt and submodules
# as the Android NDK build, just a different CMAKE_SYSTEM_NAME/sysroot/arch triple, guarded by the
# `if(ANDROID)` block around the JNI target. :lib:native's buildTiffCoreForIos Gradle task runs
# this automatically before iOS cinterop, so it doesn't normally need to be run by hand. Output
# defaults to this module's own build/ios/ (so `clean` reaches it); pass an output dir explicitly
# to override.
#
# Requires Xcode (for the iOS SDKs/toolchain) and a CMake new enough to drive the Xcode generator
# for iOS cross-compilation cleanly. The NDK-side CMake 3.22.1 pinned in lib/native/build.gradle.kts
# hits a known CMake/Xcode incompatibility building for iOS (the compiler-ID probe project fails to
# code-sign under recent Xcode), so this script looks for a newer CMake instead. That's a wholly
# separate build leg from the Android one the 3.22.1 pin is about; it doesn't change that pin.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${1:-${SCRIPT_DIR}/../../../build/ios}"
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
DEPLOYMENT_TARGET="13.0"

CMAKE_BIN="${TIFFRENDERER_IOS_CMAKE:-}"
if [[ -z "$CMAKE_BIN" ]]; then
  for candidate in "$HOME"/Library/Android/sdk/cmake/*/bin/cmake; do
    [[ -x "$candidate" ]] || continue
    CMAKE_BIN="$candidate"
  done
fi
if [[ -z "$CMAKE_BIN" || "$("$CMAKE_BIN" --version | head -1)" == *"3.22."* ]]; then
  echo "error: need a CMake newer than 3.22.x to configure the iOS build (3.22.1's Xcode-generator" >&2
  echo "compiler-ID probe fails to code-sign under recent Xcode). Install one, e.g.:" >&2
  echo "    android sdk install cmake/3.31.6" >&2
  echo "or point TIFFRENDERER_IOS_CMAKE at an existing newer cmake binary." >&2
  exit 1
fi
echo "Using cmake: ${CMAKE_BIN} ($("$CMAKE_BIN" --version | head -1))"

build_target() {
  local target_name="$1" sdk="$2" arch="$3"
  local sysroot build_dir lib

  sysroot="$(xcrun --sdk "$sdk" --show-sdk-path)"
  build_dir="$(mktemp -d)"

  echo "=== ${target_name} (sdk=${sdk} arch=${arch}) ==="
  "$CMAKE_BIN" -G Xcode \
    -S "$SCRIPT_DIR" \
    -B "$build_dir" \
    -DCMAKE_SYSTEM_NAME=iOS \
    -DCMAKE_OSX_ARCHITECTURES="$arch" \
    -DCMAKE_OSX_SYSROOT="$sysroot" \
    -DCMAKE_OSX_DEPLOYMENT_TARGET="$DEPLOYMENT_TARGET" \
    -DCMAKE_XCODE_ATTRIBUTE_CODE_SIGNING_ALLOWED=NO

  "$CMAKE_BIN" --build "$build_dir" --target tiffrenderer_core --config Release

  lib="$(find "$build_dir" -maxdepth 2 -name libtiffrenderer_core.a -print -quit)"
  if [[ -z "$lib" ]]; then
    echo "error: libtiffrenderer_core.a not produced for ${target_name}" >&2
    exit 1
  fi

  mkdir -p "${OUT_DIR}/${target_name}"
  cp "$lib" "${OUT_DIR}/${target_name}/libtiffrenderer_core.a"
  cp "${SCRIPT_DIR}/tiff_core.h" "${OUT_DIR}/${target_name}/tiff_core.h"

  # tiffrenderer_core only *links against* tiff (a static-library dependency isn't merged in by
  # CMake/libtool: it just records the transitive link requirement), and libtiff's own CMake in
  # turn does the same for libjpeg/libwebp. tiffcore.def needs every one of these as its own
  # staticLibraries entry, so collect them all rather than assuming they're folded together.
  local dep
  for dep_pattern in \
      "third_party/libtiff/libtiff/Release-*/tiff.framework/tiff:libtiff.a" \
      "Release-*/libijg_jpeg.a:libijg_jpeg.a" \
      "third_party/libwebp/Release-*/libwebp.a:libwebp.a" \
      "third_party/libwebp/Release-*/libsharpyuv.a:libsharpyuv.a"; do
    local src_glob="${dep_pattern%%:*}"
    local dst_name="${dep_pattern##*:}"
    dep="$(find "$build_dir" -path "${build_dir}/${src_glob}" -print -quit)"
    if [[ -n "$dep" ]]; then
      cp "$dep" "${OUT_DIR}/${target_name}/${dst_name}"
    fi
  done

  rm -rf "$build_dir"
  echo "-> ${OUT_DIR}/${target_name}/ ($(ls "${OUT_DIR}/${target_name}" | tr '\n' ' '))"
}

rm -rf "$OUT_DIR"
build_target iosArm64 iphoneos arm64
build_target iosSimulatorArm64 iphonesimulator arm64
# No iosX64 (Intel simulator): Apple/Xcode itself has dropped support for it, so
# :multiplatform doesn't declare that Kotlin/Native target either; nothing consumes this leg.

echo "Done. Output in ${OUT_DIR}/{iosArm64,iosSimulatorArm64}/"
