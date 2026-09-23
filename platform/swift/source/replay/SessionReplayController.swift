// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
internal import CapturePassable
import Foundation
import UIKit

final class SessionReplayController {
    private let queue = DispatchQueue.serial(withLabelSuffix: "ReplayController", target: .default)
    private let replay: Replay = Replay()
    private let screenshotCaptureLock = Lock()
    private var screenshotCaptureInProgress = false
    private var deviceCommandScreenshotRequestID: UInt64?

    var logger: CoreLogging?

    init(configuration: SessionReplayConfiguration) {
        for (className, annotatedView) in configuration.categorizers {
            self.replay.add(knownClass: className, type: annotatedView)
        }
    }
}

extension SessionReplayController: CapturePassable.SessionReplayTarget {
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

    func captureDeviceCommandScreenshot(_ requestID: UInt64) {
        guard startDeviceCommandScreenshotCapture(requestID: requestID) else {
            capture_complete_device_command_screenshot(requestID, nil)
            return
        }
        captureDeviceCommandScreenshotJPEG()
    }

    private func startDeviceCommandScreenshotCapture(requestID: UInt64) -> Bool {
        return self.screenshotCaptureLock.withLock {
            guard !self.screenshotCaptureInProgress else {
                return false
            }
            self.screenshotCaptureInProgress = true
            self.deviceCommandScreenshotRequestID = requestID
            return true
        }
    }

    private func captureDeviceCommandScreenshotJPEG() {
        DispatchQueue.main.async { [weak self] in
            guard let window = UIApplication.shared.sessionReplayWindows().first else {
                self?.completeDeviceCommandScreenshotCapture(jpeg: nil)
                return
            }

            let format = UIGraphicsImageRendererFormat()
            format.scale = 1.0

            let bounds = UIScreen.main.bounds.size
            let jpeg = UIGraphicsImageRenderer(size: bounds, format: format)
                .jpegData(withCompressionQuality: 0.1) { context in
                    window.layer.render(in: context.cgContext)
                }

            self?.queue.async { [weak self] in
                self?.completeDeviceCommandScreenshotCapture(jpeg: jpeg)
            }
        }
    }

    private func completeDeviceCommandScreenshotCapture(jpeg: Data?) {
        let requestID = self.screenshotCaptureLock.withLock {
            self.screenshotCaptureInProgress = false
            let requestID = self.deviceCommandScreenshotRequestID
            self.deviceCommandScreenshotRequestID = nil
            return requestID
        }

        guard let requestID else {
            return
        }
        capture_complete_device_command_screenshot(requestID, jpeg)
    }
}
