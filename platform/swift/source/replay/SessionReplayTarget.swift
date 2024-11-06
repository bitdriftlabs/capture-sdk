// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CapturePassable
import Foundation
import UIKit

final class SessionReplayTarget {
    private let queue = DispatchQueue.serial(withLabelSuffix: "ReplayController", target: .default)
    private let replay: Replay = Replay()

    var logger: CoreLogging?

    init(configuration: SessionReplayConfiguration) {
        for (className, annotatedView) in configuration.categorizers {
            self.replay.add(knownClass: className, type: annotatedView)
        }
    }
}

extension SessionReplayTarget: CapturePassable.SessionReplayTarget {
    func captureScreen() {
        DispatchQueue.main.async { [weak self] in
            let start = Uptime()
            guard let capturedScreen = self?.replay.capture() else {
                return
            }

            let duration = Uptime().timeIntervalSince(start)

            self?.queue.async {
                self?.logger?.logSessionReplayScreen(
                    screen: SessionReplayCapture(data: capturedScreen),
                    duration: duration
                )
            }
        }
    }

    func captureScreenshot() {
        DispatchQueue.main.async {
            guard let window = UIApplication.shared.sessionReplayWindows().first else {
                self.logger?.logSessionReplayScreenshot(
                    screen: nil,
                    duration: 0
                )
                return
            }

            let layer = window.layer
            let bounds = UIScreen.main.bounds.size

            self.queue.async { [weak self] in
                let start = Uptime()
                let format = UIGraphicsImageRendererFormat()
                format.scale = 1.0

                let renderer = UIGraphicsImageRenderer(size: bounds, format: format)
                let jpeg = renderer.jpegData(withCompressionQuality: 0.1) { context in
                    layer.render(in: context.cgContext)
                }
                self?.logger?.logSessionReplayScreenshot(
                    screen: SessionReplayCapture(data: jpeg),
                    duration: Uptime().timeIntervalSince(start)
                )
            }
        }
    }
}
