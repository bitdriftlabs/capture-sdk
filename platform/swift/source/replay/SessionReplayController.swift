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

final class SessionReplayController: @unchecked Sendable {
    private let queue = DispatchQueue.serial(withLabelSuffix: "ReplayController", target: .default)
    private let replay: Replay = Replay()
    private let screenshotCaptureLock = Lock()
    private var screenshotCaptureInProgress = false
    private var screenshotCompletion: ((Data?) -> Void)?
    private let captureScreenshot: ((@escaping @Sendable (Data?) -> Void) -> Void)?

    weak var logger: CoreLogging?

    init(
        configuration: SessionReplayConfiguration,
        captureScreenshot: ((@escaping @Sendable (Data?) -> Void) -> Void)? = nil
    ) {
        self.captureScreenshot = captureScreenshot
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

    func captureCommandScreenshot() async -> Result<CommandResult, CommandError> {
        await withCheckedContinuation { continuation in
            guard startScreenshotCapture(completion: { jpeg in
                guard let jpeg, jpeg.count >= 4, jpeg.count <= 2 * 1024 * 1024,
                      jpeg.starts(with: [0xff, 0xd8]), jpeg.suffix(2).elementsEqual([0xff, 0xd9])
                else {
                    continuation.resume(returning: .failure(CommandError(title: "invalid or unavailable screenshot")))
                    return
                }
                continuation.resume(returning: .success(CommandResult(
                    attachment: CommandAttachment(data: jpeg, mimeType: "image/jpeg")
                )))
            }) else {
                continuation.resume(returning: .failure(CommandError(title: "screenshot capture already in progress")))
                return
            }
            if let captureScreenshot {
                captureScreenshot { [self] in completeDeviceCommandScreenshotCapture(jpeg: $0) }
            } else {
                captureDeviceCommandScreenshotJPEG()
            }
        }
    }

    private func startScreenshotCapture(completion: @escaping (Data?) -> Void) -> Bool {
        return self.screenshotCaptureLock.withLock {
            guard !self.screenshotCaptureInProgress else {
                return false
            }
            self.screenshotCaptureInProgress = true
            self.screenshotCompletion = completion
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
        let completion = self.screenshotCaptureLock.withLock {
            self.screenshotCaptureInProgress = false
            let completion = self.screenshotCompletion
            self.screenshotCompletion = nil
            return completion
        }

        completion?(jpeg)
    }
}
