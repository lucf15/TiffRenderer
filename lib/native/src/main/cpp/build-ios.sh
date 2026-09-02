#!/usr/bin/env bash
set -euo pipefail

# Cross-compiles tiffrenderer_core (vendored libtiff/libjpeg/libwebp + tiff_core.cpp/tiff_io.cpp,
# no JNI) as a static library per iOS Kotlin/Native target; runs automatically via
# :lib:native's buildTiffCoreForIos Gradle task, so it doesn't normally need to be run by hand.
#
# Needs a CMake newer than the NDK-pinned 3.22.1: that version's Xcode-generator compiler-ID probe
# fails to code-sign under recent Xcode. A separate concern from the Android NDK CMake pin itself.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${1:-${SCRIPT_DIR}/../../../build/ios}"
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
DEPLOYMENT_TARGET="13.0"

CMAKE_BIN="${TIFFRENDERER_SDK_CMAKE:-}"
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
  echo "or point TIFFRENDERER_SDK_CMAKE at an existing newer cmake binary." >&2
  exit 1
fi
echo "Using cmake: ${CMAKE_BIN} ($("$CMAKE_BIN" --version | head -1))"

build_target() {
  local target_name="$1" sdk="$2" arch="$3"
  local sysroot build_dir lib

  sysroot="$(xcrun --sdk "$sdk" --show-sdk-path)"
  build_dir="$(mktemp -d)"

  local werror_arg=""
  if [[ "${TIFFRENDERER_WERROR:-}" == "true" ]]; then
    werror_arg="-DTIFFRENDERER_WERROR=ON"
  fi

  echo "=== ${target_name} (sdk=${sdk} arch=${arch}) ==="
  "$CMAKE_BIN" -G Xcode \
    -S "$SCRIPT_DIR" \
    -B "$build_dir" \
    -DCMAKE_SYSTEM_NAME=iOS \
    -DCMAKE_OSX_ARCHITECTURES="$arch" \
    -DCMAKE_OSX_SYSROOT="$sysroot" \
    -DCMAKE_OSX_DEPLOYMENT_TARGET="$DEPLOYMENT_TARGET" \
    -DCMAKE_XCODE_ATTRIBUTE_CODE_SIGNING_ALLOWED=NO \
    ${werror_arg}

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
