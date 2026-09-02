#!/usr/bin/env bash
set -euo pipefail

# Cross-compiles tiffrenderer_core (vendored libtiff/libjpeg/libwebp + tiff_core.cpp/tiff_io.cpp)
# into a single wasmJs ES module (tiffcore_module.mjs/.wasm) exporting the tiffcore_* C API
# directly; runs automatically via :lib:native's buildTiffCoreForWasm Gradle task.
#
# Needs Emscripten (emcmake/emcc) on PATH: brew install emscripten, or source emsdk_env.sh from a
# manual https://emscripten.org/docs/getting_started/downloads.html install.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${1:-${SCRIPT_DIR}/../../../build/wasm}"
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

if ! command -v emcmake >/dev/null 2>&1; then
  echo "error: emcmake not found on PATH. Install Emscripten, e.g.:" >&2
  echo "    brew install emscripten" >&2
  echo "or source emsdk_env.sh from a manual emsdk install." >&2
  exit 1
fi

PATH="$(dirname "$(command -v emcmake)"):$PATH" embuilder build zlib

CMAKE_BIN="${TIFFRENDERER_SDK_CMAKE:-$(command -v cmake || true)}"
if [[ -z "$CMAKE_BIN" ]]; then
  for candidate in "$HOME"/Library/Android/sdk/cmake/*/bin/cmake; do
    [[ -x "$candidate" ]] || continue
    CMAKE_BIN="$candidate"
  done
fi
if [[ -z "$CMAKE_BIN" ]]; then
  echo "error: no cmake found. Install one, e.g.: android sdk install cmake/3.31.6" >&2
  exit 1
fi
echo "Using cmake: ${CMAKE_BIN} ($("$CMAKE_BIN" --version | head -1))"

build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT

werror_arg=""
if [[ "${TIFFRENDERER_WERROR:-}" == "true" ]]; then
  werror_arg="-DTIFFRENDERER_WERROR=ON"
fi

PATH="$(dirname "$(command -v emcmake)"):$PATH" emcmake "$CMAKE_BIN" \
  -S "$SCRIPT_DIR" \
  -B "$build_dir" \
  -DCMAKE_BUILD_TYPE=Release \
  ${werror_arg}

"$CMAKE_BIN" --build "$build_dir" --target tiffcore_module --config Release

module_js="$(find "$build_dir" -maxdepth 1 -name tiffcore_module.mjs -print -quit)"
module_wasm="$(find "$build_dir" -maxdepth 1 -name tiffcore_module.wasm -print -quit)"
if [[ -z "$module_js" || -z "$module_wasm" ]]; then
  echo "error: tiffcore_module.mjs/.wasm not produced" >&2
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
cp "$module_js" "$module_wasm" "$OUT_DIR/"
cp "${SCRIPT_DIR}/tiffcore-glue.mjs" "${OUT_DIR}/tiffcore-glue.mjs"
cp "${SCRIPT_DIR}/tiffcore-worker.mjs" "${OUT_DIR}/tiffcore-worker.mjs"

echo "Done. Output in ${OUT_DIR}/"
