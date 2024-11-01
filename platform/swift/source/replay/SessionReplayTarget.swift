@_implementationOnly import CapturePassable
import Foundation

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

    func captureScreenshot() {}
}
