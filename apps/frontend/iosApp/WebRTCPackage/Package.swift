// swift-tools-version:5.9
// Wraps the prebuilt WebRTC-SDK used by ktor-client-webrtc on iOS.
// The upstream package (github.com/webrtc-sdk/Specs) fails to resolve on newer Xcode
// because of its visionOS platform entry, so we point at the same release artifact here.
import PackageDescription

let package = Package(
    name: "WebRTC",
    platforms: [.iOS(.v13)],
    products: [
        .library(name: "WebRTC", targets: ["WebRTC"]),
    ],
    targets: [
        .binaryTarget(
            name: "WebRTC",
            url: "https://github.com/webrtc-sdk/Specs/releases/download/137.7151.04/WebRTC.xcframework.zip",
            checksum: "d8e3cae48e96dc0e6acc8ab485d5b51971c058524d0f42abbda0309a0ead2e4e"
        ),
    ]
)
