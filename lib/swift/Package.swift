// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TiffRenderer",
    platforms: [.iOS(.v15)],
    products: [.library(name: "TiffRenderer", targets: ["TiffRenderer"])],
    targets: [
        .binaryTarget(name: "TiffRendererCore", path: "Frameworks/TiffRendererCore.xcframework"),
        .target(name: "TiffRenderer", dependencies: ["TiffRendererCore"]),
    ]
)
