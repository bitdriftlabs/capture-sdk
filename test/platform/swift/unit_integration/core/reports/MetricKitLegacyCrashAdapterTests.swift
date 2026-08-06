// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import MetricKit
import XCTest

#if compiler(>=6.4)

@available(iOS 27.0, *)
final class MetricKitLegacyCrashAdapterTests: XCTestCase {
    func testMakeCrashDictProducesCallStackTreeAndDiagnosticMetadata() throws {
        let report = try self.loadDiagnosticReport("metrickit-ios27-crash-example.json")
        guard case let .crash(diagnostic) = report.result else {
            XCTFail("expected a crash diagnostic")
            return
        }

        let dict = MetricKitLegacyCrashAdapter.makeCrashDict(
            diagnostic: diagnostic, environment: report.environment)

        let callStackTree = try XCTUnwrap(dict["callStackTree"] as? [String: Any])
        let callStacks = try XCTUnwrap(callStackTree["callStacks"] as? [[String: Any]])
        XCTAssertEqual(1, callStacks.count)

        let thread = callStacks[0]
        XCTAssertEqual(true, thread["threadAttributed"] as? Bool)

        let rootFrames = try XCTUnwrap(thread["callStackRootFrames"] as? [[String: Any]])
        XCTAssertEqual(1, rootFrames.count)
        let frame = rootFrames[0]
        XCTAssertEqual("11111111-1111-1111-1111-111111111111", frame["binaryUUID"] as? String)
        XCTAssertEqual("TestBinary", frame["binaryName"] as? String)
        XCTAssertEqual(UInt64(4_295_000_010), frame["address"] as? UInt64)
        XCTAssertEqual(UInt64(10), frame["offsetIntoBinaryTextSegment"] as? UInt64)
        XCTAssertEqual(0, (frame["subFrames"] as? [[String: Any]])?.count)

        let metadata = try XCTUnwrap(dict["diagnosticMetaData"] as? [String: Any])
        XCTAssertEqual(11, metadata["signal"] as? Int)
        XCTAssertEqual(12345, metadata["pid"] as? Int)
    }

    func testMakeMemoryExceptionDictProducesCallStackTreeWithoutDiagnosticMetadata() throws {
        let report = try self.loadDiagnosticReport("metrickit-ios27-memoryexception-example.json")
        guard case let .memoryException(diagnostic) = report.result else {
            XCTFail("expected a memory exception diagnostic")
            return
        }

        let dict = MetricKitLegacyCrashAdapter.makeMemoryExceptionDict(diagnostic: diagnostic)

        XCTAssertNil(dict["diagnosticMetaData"])
        let callStackTree = try XCTUnwrap(dict["callStackTree"] as? [String: Any])
        let callStacks = try XCTUnwrap(callStackTree["callStacks"] as? [[String: Any]])
        XCTAssertEqual(1, callStacks.count)

        let rootFrames = try XCTUnwrap(callStacks[0]["callStackRootFrames"] as? [[String: Any]])
        XCTAssertEqual(
            "22222222-2222-2222-2222-222222222222", rootFrames[0]["binaryUUID"] as? String)
    }

    func testMakeMetadataDictRecombinesOsVersionAndCopiesEnvironmentFields() throws {
        let report = try self.loadDiagnosticReport("metrickit-ios27-crash-example.json")

        let metadata = MetricKitLegacyCrashAdapter.makeMetadataDict(environment: report.environment)

        XCTAssertEqual("com.example.app", metadata["bundleIdentifier"] as? String)
        XCTAssertEqual("1", metadata["appBuildVersion"] as? String)
        XCTAssertEqual("US", metadata["regionFormat"] as? String)
        XCTAssertEqual("iPhone17,2", metadata["deviceType"] as? String)
        XCTAssertEqual("arm64e", metadata["platformArchitecture"] as? String)
        XCTAssertEqual("iPhone OS 27.0 (24A5380h)", metadata["osVersion"] as? String)
        XCTAssertEqual(false, metadata["lowPowerModeEnabled"] as? Bool)
    }
}

@available(iOS 27.0, *)
private extension MetricKitLegacyCrashAdapterTests {
    func loadDiagnosticReport(_ resourceName: String) throws -> DiagnosticReport {
        let testBundle = Bundle(for: type(of: self))
        let url = try XCTUnwrap(testBundle.url(forResource: resourceName, withExtension: nil))
        return try JSONDecoder().decode(DiagnosticReport.self, from: Data(contentsOf: url))
    }
}

#endif
