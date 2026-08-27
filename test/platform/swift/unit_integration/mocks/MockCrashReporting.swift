// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import CaptureLoggerBridge
import Foundation

public final class MockCrashReporting: NSObject, CrashReporting {
    let previousCrash: BitdriftPreviousCrash?

    public init(previousCrash: BitdriftPreviousCrash? = nil) {
        self.previousCrash = previousCrash
        super.init()
    }

    public func cachedCrashDate() -> Date? { nil }
    public func cachedPreviousCrash() -> BitdriftPreviousCrash? { previousCrash }
    public func enhancedMetricKitReport(
        _ metricKitReport: [String: Any],
        useStackOverlapMatching: Bool,
        summaryOut: AutoreleasingUnsafeMutablePointer<NSDictionary?>?
    ) -> [String: Any] { metricKitReport }
}
