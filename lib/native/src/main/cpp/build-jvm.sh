#!/usr/bin/env bash
set -euo pipefail

# Builds the desktop JNI shim (tiffrenderer_jni_jvm) for whatever OS/arch runs this script, always
# host-native, never cross-compiled. :lib:core's buildTiffRendererJniForJvm Gradle task runs this
# automatically before jvm resource processing.
#
# Args: $1 output dir (default: this module's own build/jvm/natives), $2 JAVA_HOME override
# (default: $JAVA_HOME), needed for CMake's find_package(JNI).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${1:-${SCRIPT_DIR}/../../../build/jvm/natives}"
JAVA_HOME_OVERRIDE="${2:-${JAVA_HOME:-}}"
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

if [[ -z "$JAVA_HOME_OVERRIDE" ]]; then
  echo "error: JAVA_HOME not set and no override passed; needed for CMake's find_package(JNI)." >&2
  exit 1
fi

CMAKE_BIN="${TIFFRENDERER_CMAKE:-$(command -v cmake || true)}"
if [[ -z "$CMAKE_BIN" ]]; then
  for candidate in "$HOME"/Library/Android/sdk/cmake/*/bin/cmake; do
    [[ -x "$candidate" ]] || continue
    CMAKE_BIN="$candidate"
  done
fi
if [[ -z "$CMAKE_BIN" ]]; then
  echo "error: no cmake found. Install one, e.g.: android sdk install cmake/3.22.1" >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin) OS_NAME="macos" ;;
  Linux) OS_NAME="linux" ;;
  MINGW*|MSYS*|CYGWIN*) OS_NAME="windows" ;;
  *) echo "error: unsupported host OS: $(uname -s)" >&2; exit 1 ;;
esac

# Derived from the JDK's own reported os.arch, not uname -m: they can genuinely differ (e.g. an
# x64 JDK running under Windows-on-ARM's x64 emulation), and os.arch is what
# TiffRendererNativeJvm.kt matches against at runtime, so the build must target that.
JAVA_ARCH="$("${JAVA_HOME_OVERRIDE}/bin/java" -XshowSettings:properties -version 2>&1 | grep -o 'os\.arch = .*' | awk '{print $3}')"
case "$JAVA_ARCH" in
  arm64|aarch64) ARCH_NAME="aarch64" ;;
  x86_64|amd64) ARCH_NAME="x86_64" ;;
  *) echo "error: unsupported JDK arch (os.arch=$JAVA_ARCH)" >&2; exit 1 ;;
esac

case "$OS_NAME" in
  macos) LIB_NAME="libtiffrenderer_jni_jvm.dylib" ;;
  linux) LIB_NAME="libtiffrenderer_jni_jvm.so" ;;
  windows) LIB_NAME="tiffrenderer_jni_jvm.dll" ;;
esac

TARGET_DIR="${OUT_DIR}/${OS_NAME}-${ARCH_NAME}"
mkdir -p "$TARGET_DIR"

build_dir="$(mktemp -d)"
cleanup_build_dir() {
  for _ in 1 2 3 4 5; do
    rm -rf "$build_dir" 2>/dev/null && return 0
    sleep 1
  done
  rm -rf "$build_dir" 2>/dev/null || true
}
trap cleanup_build_dir EXIT
echo "Using cmake: ${CMAKE_BIN} ($("$CMAKE_BIN" --version | head -1))"
echo "Targeting JDK arch: ${ARCH_NAME} (os.arch=${JAVA_ARCH})"

WERROR_CMAKE_ARG=""
if [[ "${TIFFRENDERER_WERROR:-}" == "true" ]]; then
  WERROR_CMAKE_ARG="-DTIFFRENDERER_WERROR=ON"
fi

if [[ "$OS_NAME" == "windows" ]]; then
  # CMake's own "Visual Studio" generator picks its C/C++ toolset by matching the *host* CPU
  # architecture, not the -A target platform; on a Windows-on-ARM host targeting x64 (this JDK's
  # arch) that mismatch leaves it unable to find cl.exe at all, even though the actual Host=ARM64/
  # Target=x64 cross tools are genuinely installed. The standard, reliable way around this is the
  # same one most mixed-arch CI pipelines use: run vcvarsall.bat for the exact host/target pair to
  # populate PATH/INCLUDE/LIB, then hand CMake the "NMake Makefiles" generator, which just uses
  # whatever cl.exe is already on that PATH instead of doing its own toolset lookup.
  case "$(uname -m)" in
    arm64|aarch64) HOST_VCVARS_ARCH="arm64" ;;
    x86_64|amd64) HOST_VCVARS_ARCH="amd64" ;;
    *) echo "error: unsupported host arch for vcvarsall: $(uname -m)" >&2; exit 1 ;;
  esac
  case "$ARCH_NAME" in
    x86_64) TARGET_VCVARS_ARCH="amd64" ;;
    aarch64) TARGET_VCVARS_ARCH="arm64" ;;
  esac
  if [[ "$HOST_VCVARS_ARCH" == "$TARGET_VCVARS_ARCH" ]]; then
    VCVARSALL_ARG="$HOST_VCVARS_ARCH"
  else
    VCVARSALL_ARG="${HOST_VCVARS_ARCH}_${TARGET_VCVARS_ARCH}"
  fi

  VSWHERE="/c/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe"
  if [[ ! -x "$VSWHERE" ]]; then
    echo "error: vswhere.exe not found; is Visual Studio installed?" >&2
    exit 1
  fi
  VS_INSTALL_PATH="$("$VSWHERE" -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath | tr -d '\r')"
  if [[ -z "$VS_INSTALL_PATH" ]]; then
    echo "error: no Visual Studio installation with the x86/x64 C++ toolset found" >&2
    exit 1
  fi

  # Windows-form (C:/...) equivalents for anything that ends up inside the .bat file below: cmd.exe
  # and vcvarsall.bat don't understand bash's own /c/... path form.
  build_dir_win="$(cd "$build_dir" && pwd -W)"
  script_dir_win="$(cd "$SCRIPT_DIR" && pwd -W)"

  case "$ARCH_NAME" in
    x86_64) VCPKG_TRIPLET="x64-windows-static"; VCPKG_HOST_TRIPLET="x64-windows" ;;
    aarch64) VCPKG_TRIPLET="arm64-windows-static"; VCPKG_HOST_TRIPLET="arm64-windows" ;;
  esac
  VCPKG_CMAKE_ARGS=""
  if [[ -n "${VCPKG_INSTALLATION_ROOT:-}" ]]; then
    VCPKG_CMAKE_ARGS="\"-DCMAKE_TOOLCHAIN_FILE=%VCPKG_INSTALLATION_ROOT%\\scripts\\buildsystems\\vcpkg.cmake\" -DVCPKG_TARGET_TRIPLET=${VCPKG_TRIPLET} -DVCPKG_HOST_TRIPLET=${VCPKG_HOST_TRIPLET} -DCMAKE_POLICY_DEFAULT_CMP0091=NEW -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded"
  fi

  JAVA_HOME_FORWARD_SLASHES="${JAVA_HOME_OVERRIDE//\\//}"

  wrapper_bat="${build_dir}/configure_and_build.bat"
  cat > "$wrapper_bat" <<BATEOF
call "${VS_INSTALL_PATH}\\VC\\Auxiliary\\Build\\vcvarsall.bat" ${VCVARSALL_ARG}
if errorlevel 1 exit /b 1
cmake -G "NMake Makefiles" -S "${script_dir_win}" -B "${build_dir_win}" -DJAVA_HOME="${JAVA_HOME_FORWARD_SLASHES}" -DCMAKE_BUILD_TYPE=Release ${WERROR_CMAKE_ARG} ${VCPKG_CMAKE_ARGS}
if errorlevel 1 exit /b 1
cmake --build "${build_dir_win}" --target tiffrenderer_jni_jvm --config Release
if errorlevel 1 exit /b 1
BATEOF
  # MSYS_NO_PATHCONV: Git Bash's automatic path-conversion heuristic otherwise mistakes the /c
  # flag for a POSIX-style reference to the C: drive root and mangles it, leaving cmd.exe with no
  # usable /c argument at all (it silently falls back to an interactive shell instead of erroring).
  MSYS_NO_PATHCONV=1 cmd.exe /c "${build_dir_win}\\configure_and_build.bat"
else
  CMAKE_ARCH_ARGS=()
  case "$OS_NAME" in
    macos)
      case "$ARCH_NAME" in
        x86_64) CMAKE_ARCH_ARGS=(-DCMAKE_OSX_ARCHITECTURES=x86_64) ;;
        aarch64) CMAKE_ARCH_ARGS=(-DCMAKE_OSX_ARCHITECTURES=arm64) ;;
      esac
      CMAKE_ARCH_ARGS+=(-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0)
      ;;
    linux)
      CMAKE_ARCH_ARGS=("-DCMAKE_SHARED_LINKER_FLAGS=-static-libgcc -static-libstdc++")
      ;;
  esac
  if [[ -n "$WERROR_CMAKE_ARG" ]]; then
    CMAKE_ARCH_ARGS+=("$WERROR_CMAKE_ARG")
  fi
  "$CMAKE_BIN" -S "$SCRIPT_DIR" -B "$build_dir" -DJAVA_HOME="$JAVA_HOME_OVERRIDE" -DCMAKE_BUILD_TYPE=Release "${CMAKE_ARCH_ARGS[@]}"
  "$CMAKE_BIN" --build "$build_dir" --target tiffrenderer_jni_jvm --config Release -j
fi

lib="$(find "$build_dir" -maxdepth 3 -name "$LIB_NAME" -print -quit)"
if [[ -z "$lib" ]]; then
  echo "error: ${LIB_NAME} not produced" >&2
  exit 1
fi
cp "$lib" "${TARGET_DIR}/${LIB_NAME}"

# .sha256 sidecar, bundled as a jvmMain resource alongside the .so/.dylib/.dll: read at runtime by
# TiffRendererNativeJvm to verify an already-extracted native before trusting it (see loadNativeLibrary).
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "${TARGET_DIR}/${LIB_NAME}" | awk '{print $1}' > "${TARGET_DIR}/${LIB_NAME}.sha256"
else
  shasum -a 256 "${TARGET_DIR}/${LIB_NAME}" | awk '{print $1}' > "${TARGET_DIR}/${LIB_NAME}.sha256"
fi

echo "Done. Output in ${TARGET_DIR}/${LIB_NAME}"
