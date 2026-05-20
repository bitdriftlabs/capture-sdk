// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import UIKit
import XCTest

// Subclass whose Swift-mangled name ends with "CGDrawingLayer", matching the suffix used by
// SwiftUI's internal text rendering layer. Used to unit test the suffix categorization path
// without depending on private SwiftUI internals.
private final class CGDrawingLayer: CALayer {}

final class ReplayCommonCategorizerTests: XCTestCase {
    func testCategorizeLayerDefaultsToView() {
        let layer = CALayer()
        layer.backgroundColor = UIColor.red.cgColor

        let result = ReplayCommonCategorizer.categorizeLayer(layer, frame: .zero)

        XCTAssertEqual(result.type, .view)
    }

    func testCategorizeLayerWithCGImageContentReturnsImage() {
        let layer = CALayer()
        let image = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1)).image { ctx in
            ctx.fill(CGRect(x: 0, y: 0, width: 1, height: 1))
        }
        layer.contents = image.cgImage

        let result = ReplayCommonCategorizer.categorizeLayer(layer, frame: .zero)

        XCTAssertEqual(result.type, .image)
    }

    func testCategorizeLayerCGDrawingLayerSuffixReturnsLabel() {
        // CGDrawingLayer (defined above) has a Swift-mangled name that ends with "CGDrawingLayer",
        // matching the same suffix pattern as SwiftUI's internal text rendering layer.
        let layer = CGDrawingLayer()
        let frame = CGRect(x: 0, y: 0, width: 100, height: 20)

        let result = ReplayCommonCategorizer.categorizeLayer(layer, frame: frame)

        XCTAssertEqual(result.type, .label)
        XCTAssertFalse(result.recurse)
        XCTAssertEqual(result.frame, frame)
    }
}
