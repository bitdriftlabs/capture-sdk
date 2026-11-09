// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class IssueReporterInitStateTests: XCTestCase {
    // MARK: - resolveRuntimeState

    func testResolveRuntimeState_whenContentsIsNil_returnsMonitoring() {
        XCTAssertEqual(.monitoring, resolveRuntimeState(from: nil))
    }

    func testResolveRuntimeState_whenFileIsUnparseable_returnsRuntimeInvalid() {
        XCTAssertEqual(.runtimeInvalid, resolveRuntimeState(from: "malformed-no-comma"))
    }

    func testResolveRuntimeState_whenCrashReportingKeyIsMissing_returnsRuntimeMissingFlag() {
        XCTAssertEqual(.runtimeMissingFlag, resolveRuntimeState(from: "other.flag,true"))
    }

    func testResolveRuntimeState_whenCrashReportingValueIsNotBool_returnsRuntimeInvalid() {
        XCTAssertEqual(.runtimeInvalid, resolveRuntimeState(from: "crash_reporting.enabled,not-a-bool"))
    }

    func testResolveRuntimeState_whenCrashReportingIsFalse_returnsRuntimeNotEnabled() {
        XCTAssertEqual(.runtimeNotEnabled, resolveRuntimeState(from: "crash_reporting.enabled,false"))
    }

    func testResolveRuntimeState_whenCrashReportingIsTrue_returnsMonitoring() {
        XCTAssertEqual(.monitoring, resolveRuntimeState(from: "crash_reporting.enabled,true"))
    }
}
