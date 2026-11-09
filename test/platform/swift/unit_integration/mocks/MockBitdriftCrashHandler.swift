// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import CaptureLoggerBridge

public final class MockBitdriftCrashHandler: NSObject, BitdriftCrashHandling {
    public var didConfigure = false
    public var didStart = false
    public var didStop = false
    public var didCallCachedPreviousCrash = false
    public var shouldThrowOnConfigure = false
    public var didCrashLastLaunchValue: NSNumber?
    public var cachedCrashDateValue: Date?
    public var previousCrash: BitdriftPreviousCrash?

    public func configure(withCrashReportDirectory _: URL) throws {
        if shouldThrowOnConfigure { throw MockError() }
        didConfigure = true
    }

    public func startCrashReporter() throws { didStart = true }
    public func stopCrashReporter() { didStop = true }
    public func didCrashLastLaunch() -> NSNumber? { didCrashLastLaunchValue }
    public func cachedCrashDate() -> Date? { cachedCrashDateValue }
    public func cachedPreviousCrash() -> BitdriftPreviousCrash? {
        didCallCachedPreviousCrash = true
        return previousCrash
    }
}
