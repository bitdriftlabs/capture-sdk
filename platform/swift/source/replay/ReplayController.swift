// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge
@_implementationOnly import CapturePassable
import Foundation

final class ReplayController {
    // Run loop retains the timer while it's being active.
    private var timer: QueueTimer?
    private let queue = DispatchQueue.serial(withLabelSuffix: "ReplayController", target: .default)

    private let logger: CoreLogging

    init(logger: CoreLogging) {
        self.logger = logger
    }

    func start(with configuration: SessionReplayConfiguration) {
        self.stop()

        guard configuration.captureIntervalSeconds > 0 else {
            assertionFailure("session replay capture interval needs to be greater than 0s")
            return
        }

        let replay = Replay()
        configuration.willStart?(replay)

        self.timer = QueueTimer.scheduledTimer(
            withTimeInterval: configuration.captureIntervalSeconds,
            queue: self.queue
        ) { [weak self] in
            self?.maybeCaptureScreen(replay: replay)
        }
    }

    func stop() {
        self.timer?.invalidate()
        self.timer = nil
    }

    // MARK: - Private

    func maybeCaptureScreen(replay: Replay) {
        guard self.logger.runtimeValue(.sessionReplay) == true else {
            return
        }

        DispatchQueue.main.async { [weak self] in
            let start = Uptime()
            let capturedScreen = replay.capture()
            let duration = Uptime().timeIntervalSince(start)

            self?.queue.async {
                self?.logger.logSessionReplay(
                    screen: SessionReplayScreenCapture(data: capturedScreen),
                    duration: duration
                )
            }
        }
    }
}
