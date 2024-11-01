import Foundation

/// Responsible for emitting session replay logs in response to provided callbacks.
@objc
public protocol SessionReplayTarget {
    /// Called to indicate that the target is supposed to prepare and emit a session replay screen log.
    func captureScreen()
    /// Called to indicate that the target is supposed to prepare and emit a session replay screenshot log.
    func captureScreenshot()
}
