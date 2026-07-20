// swift-tools-version: 5.9
// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import PackageDescription

let package = Package(
    name: "capture_flutter",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(name: "capture-flutter", targets: ["capture_flutter"])
    ],
    dependencies: [
        .package(path: "/Users/faguilera/development/capture-ios")
    ],
    targets: [
        .target(
            name: "capture_flutter",
            dependencies: [
                .product(name: "Capture", package: "capture-ios"),
                .product(name: "CaptureIntegrations", package: "capture-ios")
            ]
        ),
    ]
)
