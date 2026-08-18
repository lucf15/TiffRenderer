#!/usr/bin/env bash
set -euo pipefail

# Builds and runs the real sample/iosApp Xcode project (adapted from JetBrains' own KMP wizard
# template) on an iOS Simulator. `xcodebuild` drives everything, including a Run Script build
# phase inside iosApp.xcodeproj that invokes
# `./gradlew :sample:shared:embedAndSignAppleFrameworkForXcode` itself: no separate Gradle step
# needed here.
#
# Also pushes every `lib/core/src/androidDeviceTest/assets/*.tif` fixture, plus (if an Android
# device/emulator is attached via `adb`) whatever's in its `/sdcard/Download/`, into the app's
# own Documents directory: mirrors the `adb push` + Downloads flow used for manual Android
# testing. Info.plist's LSSupportsOpeningDocumentsInPlace/UIFileSharingEnabled make that
# directory show up in the Files app under "On My iPhone", where
# UIDocumentPickerViewController can browse to it.
#
# Requires Xcode and a booted-or-bootable iOS Simulator runtime.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUNDLE_ID="io.github.lucf15.tiffrenderer.sample"
DEVICE_NAME="${TIFFRENDERER_IOS_SIMULATOR:-iPhone 17}"
FIXTURES_DIR="${REPO_ROOT}/lib/core/src/androidDeviceTest/assets"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

echo "=== 1/4: building sample/iosApp via xcodebuild ==="
xcodebuild -project "${SCRIPT_DIR}/iosApp/iosApp.xcodeproj" \
  -scheme iosApp \
  -sdk iphonesimulator \
  -destination "platform=iOS Simulator,name=${DEVICE_NAME}" \
  -configuration Debug \
  -derivedDataPath "${SCRIPT_DIR}/build/ios-derived-data" \
  build

APP_PATH="$(find "${SCRIPT_DIR}/build/ios-derived-data/Build/Products/Debug-iphonesimulator" -maxdepth 1 -iname "*.app" | head -1)"
if [[ -z "$APP_PATH" ]]; then
  echo "error: no .app found under ${SCRIPT_DIR}/build/ios-derived-data" >&2
  exit 1
fi

echo "=== 2/4: booting simulator '${DEVICE_NAME}' ==="
DEVICE_ID="$(xcrun simctl list devices available -j | python3 -c "
import json, sys
data = json.load(sys.stdin)
name = '${DEVICE_NAME}'
for runtime, devices in data['devices'].items():
    for d in devices:
        if d['name'] == name and d['isAvailable']:
            print(d['udid'])
            sys.exit(0)
sys.exit(1)
")"
if [[ -z "$DEVICE_ID" ]]; then
  echo "error: no available simulator named '${DEVICE_NAME}' (set TIFFRENDERER_IOS_SIMULATOR to override)" >&2
  exit 1
fi

xcrun simctl boot "$DEVICE_ID" 2>/dev/null || true  # already-booted is not an error
open -a Simulator

echo "=== 3/4: installing ${BUNDLE_ID} ==="
# A stale prior install occasionally makes a fresh `install` fail with "Uninstall requested
# error" (simctl-internal state, not something wrong with this .app): clearing it first makes
# the script idempotent across repeated runs.
xcrun simctl uninstall "$DEVICE_ID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl install "$DEVICE_ID" "$APP_PATH"

echo "=== 4/4: pushing test fixtures into the app's Documents directory ==="
DATA_CONTAINER="$(xcrun simctl get_app_container "$DEVICE_ID" "$BUNDLE_ID" data)"
mkdir -p "${DATA_CONTAINER}/Documents"
cp "${FIXTURES_DIR}"/*.tif "${DATA_CONTAINER}/Documents/"

if [[ -x "$ADB" ]] && "$ADB" get-state >/dev/null 2>&1; then
  ANDROID_PULL_DIR="$(mktemp -d)"
  "$ADB" pull /sdcard/Download/ "$ANDROID_PULL_DIR/" >/dev/null 2>&1 || true
  if compgen -G "${ANDROID_PULL_DIR}/Download/*.tif*" > /dev/null; then
    cp "${ANDROID_PULL_DIR}"/Download/*.tif* "${DATA_CONTAINER}/Documents/"
    echo "also pulled $(ls "${ANDROID_PULL_DIR}"/Download/*.tif* | wc -l | tr -d ' ') files from the attached Android device's /sdcard/Download/"
  fi
  rm -rf "$ANDROID_PULL_DIR"
else
  echo "(no attached Android device via adb: only repo fixtures pushed)"
fi

echo "pushed $(ls "${DATA_CONTAINER}/Documents" | wc -l | tr -d ' ') total files to ${DATA_CONTAINER}/Documents/"

xcrun simctl launch "$DEVICE_ID" "$BUNDLE_ID"

echo "Done. Running on simulator ${DEVICE_ID} (${DEVICE_NAME})."
echo "In the app: Choose TIFF file -> Browse -> On My iPhone -> TiffSample to see the pushed fixtures."
