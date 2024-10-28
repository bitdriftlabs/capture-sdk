@_implementationOnly import CapturePassable
import Foundation

final class SessionReplayController {
    private let queue = DispatchQueue.serial(withLabelSuffix: "ReplayController", target: .default)
    // Lazy so that it's initialized only if Session Replay is enabled.
    private lazy var replay: Replay = Replay()

    var logger: CoreLogging?
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

    func captureScreenshot() {
        //        DispatchQueue.main.async {
        //            let start = Uptime()
        //            // capture screenshot here
        //            // let capturedScreenshot = ....
        //            let duration = Uptime().timeIntervalSince(start)
        //
        //            self.queue.async {
        //                self.logger?.logSessionReplayScreenshot(
        //                    screen: SessionReplayCapture(data: capturedScreenshot),
        //                    duration: duration
        //                )
        //            }
        //        }
    }
}
