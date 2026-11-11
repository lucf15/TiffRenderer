#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$TASK_ROOT"
./gradlew :lib:core:assembleTiffRendererCoreReleaseXCFramework
xcrun swiftc -swift-version 6 -typecheck \
  -sdk "$(xcrun --sdk iphonesimulator --show-sdk-path)" -target arm64-apple-ios15.0-simulator \
  -F lib/core/build/bin/iosSimulatorArm64/releaseFramework lib/swift/Sources/TiffRenderer/TiffRenderer.swift
xcrun swiftc -swift-version 6 -typecheck \
  -sdk "$(xcrun --sdk iphoneos --show-sdk-path)" -target arm64-apple-ios15.0 \
  -F lib/core/build/bin/iosArm64/releaseFramework lib/swift/Sources/TiffRenderer/TiffRenderer.swift
mkdir -p lib/swift/Frameworks
cp -R lib/core/build/XCFrameworks/release/TiffRendererCore.xcframework lib/swift/Frameworks/
