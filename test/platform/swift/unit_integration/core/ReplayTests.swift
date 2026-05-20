// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import MapKit
import SwiftUI
import UIKit
import WebKit
import XCTest

final class ReplayTests: XCTestCase {
    private var window: UIWindow!

    override func setUp() {
        self.window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        self.window.isHidden = false
    }

    override func tearDown() {
        self.window = nil
    }

    // MARK: - traverse CALayer tree

    func testCaptureIncludesVisibleOrphanLayer() {
        let container = UIView(frame: CGRect(x: 0, y: 500, width: 300, height: 200))
        window.addSubview(container)

        let orphan = CALayer()
        orphan.frame = CGRect(x: 17, y: 23, width: 80, height: 40)
        orphan.backgroundColor = UIColor.red.cgColor
        container.layer.addSublayer(orphan)

        let entries = decode(Replay().capture(windows: [window]))

        // frame = container origin (0, 500) + orphan origin (17, 23)
        XCTAssertTrue(
            entries.contains {
                $0.type == .view &&
                    $0.frame == CGRect(x: 17, y: 523, width: 80, height: 40)
            }
        )
    }

    func testCaptureExcludesHiddenOrphanLayer() {
        let container = UIView(frame: CGRect(x: 0, y: 500, width: 300, height: 200))
        window.addSubview(container)

        let orphan = CALayer()
        orphan.frame = CGRect(x: 17, y: 23, width: 80, height: 40)
        orphan.backgroundColor = UIColor.red.cgColor
        orphan.isHidden = true
        container.layer.addSublayer(orphan)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertFalse(
            entries.contains {
                $0.frame == CGRect(x: 17, y: 523, width: 80, height: 40)
            }
        )
    }

    func testCaptureExcludesLowOpacityOrphanLayer() {
        let container = UIView(frame: CGRect(x: 0, y: 500, width: 300, height: 200))
        window.addSubview(container)

        let orphan = CALayer()
        orphan.frame = CGRect(x: 17, y: 23, width: 80, height: 40)
        orphan.backgroundColor = UIColor.red.cgColor
        orphan.opacity = 0.05
        container.layer.addSublayer(orphan)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertFalse(
            entries.contains {
                $0.frame == CGRect(x: 17, y: 523, width: 80, height: 40)
            }
        )
    }

    func testOrphanLayersArePaintedBeforeUIViewSubviews() {
        let container = UIView(frame: CGRect(x: 0, y: 500, width: 300, height: 200))
        window.addSubview(container)

        let child = UIView(frame: CGRect(x: 50, y: 60, width: 70, height: 50))
        child.backgroundColor = UIColor.blue
        container.addSubview(child)

        let orphan = CALayer()
        orphan.frame = CGRect(x: 10, y: 10, width: 100, height: 100)
        orphan.backgroundColor = UIColor.red.cgColor
        container.layer.addSublayer(orphan)

        let entries = decode(Replay().capture(windows: [window]))

        let orphanIndex = entries.firstIndex { $0.frame == CGRect(x: 10, y: 510, width: 100, height: 100) }
        let childIndex = entries.firstIndex { $0.frame == CGRect(x: 50, y: 560, width: 70, height: 50) }

        guard let orphanIndex, let childIndex else {
            XCTFail("Expected both orphan layer and child view in capture output")
            return
        }

        XCTAssertLessThan(orphanIndex, childIndex, "Orphan layer should be painted before UIView subview (z-order)")
    }

    // MARK: - SwiftUI labels

    func testSwiftUITextProducesLabelEntries() {
        let hosting = UIHostingController(rootView: Text("Hello World"))
        hosting.view.frame = CGRect(x: 0, y: 400, width: 200, height: 60)
        hosting.view.backgroundColor = .clear
        window.addSubview(hosting.view)
        hosting.view.layoutIfNeeded()

        // Waiting for SwiftUI to render the Text/label
        RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.1))

        let entries = decode(Replay().capture(windows: [window]))

        let hostingFrame = CGRect(x: 0, y: 400, width: 200, height: 60)
        XCTAssertTrue(
            entries.contains { $0.type == .label && hostingFrame.contains($0.frame) },
            )
    }

    // MARK: - UITextView

    func testCaptureIncludesTextViewWithClearBackground() {
        let textView = UITextView(frame: CGRect(x: 20, y: 500, width: 300, height: 150))
        textView.backgroundColor = UIColor.clear
        textView.text = "Some text inside a text view"
        window.addSubview(textView)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertTrue(
            entries.contains {
                $0.type == .textInput && $0.frame == CGRect(x: 20, y: 500, width: 300, height: 150)
            }
        )
    }

    // MARK: - UILabel

    func testUILabelProducesLabelEntry() {
        let label = UILabel(frame: CGRect(x: 20, y: 400, width: 200, height: 40))
        label.text = "Hello Label"
        window.addSubview(label)

        let entries = decode(Replay().capture(windows: [window]))

        let labelFrame = CGRect(x: 20, y: 400, width: 200, height: 40)
        XCTAssertTrue(
            entries.contains {
                $0.type == .label && labelFrame.contains($0.frame)
            }
        )
    }

    // MARK: - UIButton

    func testUIButtonProducesButtonEntry() {
        let button = UIButton(frame: CGRect(x: 20, y: 400, width: 120, height: 44))
        button.setTitle("Tap", for: .normal)
        window.addSubview(button)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertTrue(
            entries.contains {
                $0.type == .button && $0.frame == CGRect(x: 20, y: 400, width: 120, height: 44)
            }
        )
    }

    // MARK: - UITextField

    func testUITextFieldProducesTextInputEntry() {
        let textField = UITextField(frame: CGRect(x: 20, y: 400, width: 200, height: 44))
        textField.backgroundColor = .white
        window.addSubview(textField)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertTrue(
            entries.contains {
                $0.type == .textInput && $0.frame == CGRect(x: 20, y: 400, width: 200, height: 44)
            }
        )
    }

    // MARK: - UIImageView

    func testUIImageViewProducesImageEntry() {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 80, height: 80)).image { ctx in
            UIColor.red.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 80, height: 80))
        }
        let imageView = UIImageView(image: image)
        imageView.frame = CGRect(x: 20, y: 400, width: 80, height: 80)
        window.addSubview(imageView)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertTrue(
            entries.contains {
                $0.type == .image && $0.frame == CGRect(x: 20, y: 400, width: 80, height: 80)
            }
        )
    }

    func testUIImageViewBehindSiblingProducesBackgroundImageEntry() {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 200, height: 100)).image { ctx in
            UIColor.blue.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 200, height: 100))
        }
        let imageView = UIImageView(image: image)
        imageView.frame = CGRect(x: 20, y: 400, width: 200, height: 100)

        let overlay = UIView(frame: CGRect(x: 40, y: 420, width: 60, height: 40))
        overlay.backgroundColor = .green

        window.addSubview(imageView)
        window.addSubview(overlay)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertTrue(
            entries.contains { $0.type == .backgroundImage },
            )
    }

    // MARK: - UISwitch

    func testUISwitchOnProducesSwitchOnEntry() {
        let uiSwitch = UISwitch()
        uiSwitch.isOn = true
        uiSwitch.frame.origin = CGPoint(x: 20, y: 400)
        window.addSubview(uiSwitch)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertTrue(entries.contains { $0.type == .switchOn })
    }

    func testUISwitchOffProducesSwitchOffEntry() {
        let uiSwitch = UISwitch()
        uiSwitch.isOn = false
        uiSwitch.frame.origin = CGPoint(x: 20, y: 400)
        window.addSubview(uiSwitch)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertTrue(entries.contains { $0.type == .switchOff })
    }

    // MARK: - MKMapView

    func testMKMapViewProducesMapEntry() {
        let mapView = MKMapView(frame: CGRect(x: 20, y: 400, width: 200, height: 150))
        window.addSubview(mapView)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertTrue(
            entries.contains {
                $0.type == .map && $0.frame == CGRect(x: 20, y: 400, width: 200, height: 150)
            }
        )
    }

    // MARK: - WKWebView

    func testWKWebViewProducesWebviewEntry() {
        let webView = WKWebView(frame: CGRect(x: 20, y: 400, width: 200, height: 150))
        window.addSubview(webView)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertTrue(
            entries.contains {
                $0.type == .webview && $0.frame == CGRect(x: 20, y: 400, width: 200, height: 150)
            }
        )
    }

    // MARK: - Transparent view

    func testSemiTransparentViewProducesTransparentViewEntry() {
        let v = UIView(frame: CGRect(x: 20, y: 400, width: 100, height: 100))
        v.backgroundColor = UIColor.red.withAlphaComponent(0.3)
        window.addSubview(v)

        let entries = decode(Replay().capture(windows: [window]))

        XCTAssertTrue(
            entries.contains {
                $0.type == .transparentView && $0.frame == CGRect(x: 20, y: 400, width: 100, height: 100)
            }
        )
    }
}

// MARK: - Helpers
extension ReplayTests {
    struct SessionReplayEntry {
        let type: ViewType
        let frame: CGRect
    }

    /// Decodes the binary buffer produced by `Replay.capture()` into readable entries.
    /// For more information, read the docs of `rectToBytes`
    ///
    /// - parameter data: The raw bytes produced by `Replay.capture()`.
    ///
    /// - returns: The decoded list of typed, framed entries.
    func decode(_ data: Data) -> [SessionReplayEntry] {
        var entries: [SessionReplayEntry] = []
        var i = data.startIndex

        while i < data.endIndex {
            let typeByte = data[i]
            i = data.index(after: i)

            guard let viewType = ViewType(rawValue: typeByte & 0x0F) else { break }

            var values = [CGFloat](repeating: 0, count: 4)
            for prop in 0 ..< 4 {
                if typeByte & (1 << (7 - prop)) != 0 {
                    guard data.distance(from: i, to: data.endIndex) >= 2 else { return entries }
                    let high = Int(data[i]) << 8
                    i = data.index(after: i)
                    values[prop] = CGFloat(high | Int(data[i]))
                    i = data.index(after: i)
                } else {
                    guard i < data.endIndex else { return entries }
                    values[prop] = CGFloat(data[i])
                    i = data.index(after: i)
                }
            }

            entries.append(SessionReplayEntry(
                type: viewType,
                frame: CGRect(x: values[0], y: values[1], width: values[2], height: values[3])
            ))
        }

        return entries
    }
}
