import Foundation

@objc
public protocol SessionReplayTarget {
    func captureScreen()
    func captureScreenshot()
}
