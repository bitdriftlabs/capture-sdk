// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import CaptureLoggerBridge
import Foundation

public class MockKSCrashHandler: NSObject, KSCrashHandling {
    public var didConfigure = false
    public var didStart = false
    public var didStop = false
    public var didCallEnhancedMetricKitReport = false
    public var shouldThrowOnConfigure = false
    public var didCrashLastLaunchValue: NSNumber?
    public var cachedCrashDateValue: Date?
    
    init(
        didConfigure: Bool = false,
        didStart: Bool = false,
        didStop: Bool = false,
        didCallEnhancedMetricKitReport: Bool = false,
        shouldThrowOnConfigure: Bool = false,
        didCrashLastLaunchValue: NSNumber? = nil,
        cachedCrashDateValue: Date? = nil
    ) {
        self.didConfigure = didConfigure
        self.didStart = didStart
        self.didStop = didStop
        self.didCallEnhancedMetricKitReport = didCallEnhancedMetricKitReport
        self.shouldThrowOnConfigure = shouldThrowOnConfigure
        self.didCrashLastLaunchValue = didCrashLastLaunchValue
        self.cachedCrashDateValue = cachedCrashDateValue
    }
    
    public func configure(withCrashReportDirectory _: URL) throws {
        if shouldThrowOnConfigure { throw MockError() }
        didConfigure = true
    }

    public func startCrashReporter() throws { didStart = true }
    public func stopCrashReporter() { didStop = true }
    public func didCrashLastLaunch() -> NSNumber? { didCrashLastLaunchValue }
    public func cachedCrashDate() -> Date? { cachedCrashDateValue }
    public func enhancedMetricKitReport(
        _ report: [String: Any],
        useStackOverlapMatching _: Bool,
        summaryOut _: AutoreleasingUnsafeMutablePointer<NSDictionary?>?
    ) -> [String: Any] {
        didCallEnhancedMetricKitReport = true
        return report
    }
}
