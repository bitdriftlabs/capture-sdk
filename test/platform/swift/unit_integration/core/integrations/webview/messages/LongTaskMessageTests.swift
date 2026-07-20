// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class LongTaskMessageTests: XCTestCase {
    private var sut: LongTaskMessage!

    func testMakeLoggingActionWithDurationAtOrAboveTwoHundredMsLogsAtWarningLevel() throws {
        try givenLongTaskMessage(durationMs: 250)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.longTask", level: .warning)
    }

    func testMakeLoggingActionWithDurationAtOrAboveHundredMsLogsAtInfoLevel() throws {
        try givenLongTaskMessage(durationMs: 150)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.longTask", level: .info)
    }

    func testMakeLoggingActionWithShortDurationLogsAtDebugLevel() throws {
        try givenLongTaskMessage(durationMs: 50)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.longTask", level: .debug)
    }

    func testMakeLoggingActionIncludesDurationAndStartTime() throws {
        try givenLongTaskMessage(durationMs: 50, startTime: 12.5)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.longTask", level: .debug) { fields in
            XCTAssertEqual(fields["_duration_ms"], "50.0")
            XCTAssertEqual(fields["_start_time"], "12.5")
        }
    }

    func testMakeLoggingActionWithoutAttributionOmitsAttributionFields() throws {
        try givenLongTaskMessage(attribution: nil)
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.longTask", level: .debug) { fields in
            XCTAssertNil(fields["_attribution_name"])
            XCTAssertNil(fields["_container_type"])
            XCTAssertNil(fields["_container_src"])
            XCTAssertNil(fields["_container_id"])
            XCTAssertNil(fields["_container_name"])
        }
    }

    func testMakeLoggingActionWithAttributionIncludesAttributionFields() throws {
        try givenLongTaskMessage(attribution: LongTaskAttribution(
            name: "script",
            containerType: "iframe",
            containerSrc: "https://example.com/frame.html",
            containerId: "frame-1",
            containerName: "ads"
        ))
        let action = whenMakingLoggingAction()
        assertWebLogAction(action, message: "webview.longTask", level: .debug) { fields in
            XCTAssertEqual(fields["_attribution_name"], "script")
            XCTAssertEqual(fields["_container_type"], "iframe")
            XCTAssertEqual(fields["_container_src"], "https://example.com/frame.html")
            XCTAssertEqual(fields["_container_id"], "frame-1")
            XCTAssertEqual(fields["_container_name"], "ads")
        }
    }
}

private extension LongTaskMessageTests {
    func givenLongTaskMessage(
        durationMs: Double = 50,
        startTime: Double = 0,
        attribution: LongTaskAttribution? = nil
    ) throws {
        let attributionJSON: String
        if let attribution {
            attributionJSON = """
            {
                "name": \(attribution.name.map { "\"\($0)\"" } ?? "null"),
                "containerType": \(attribution.containerType.map { "\"\($0)\"" } ?? "null"),
                "containerSrc": \(attribution.containerSrc.map { "\"\($0)\"" } ?? "null"),
                "containerId": \(attribution.containerId.map { "\"\($0)\"" } ?? "null"),
                "containerName": \(attribution.containerName.map { "\"\($0)\"" } ?? "null")
            }
            """
        } else {
            attributionJSON = "null"
        }

        let json = """
        {
            "tag": "bitdrift-webview-sdk",
            "v": 1,
            "type": "longTask",
            "timestamp": 1700000000000,
            "durationMs": \(durationMs),
            "startTime": \(startTime),
            "attribution": \(attributionJSON)
        }
        """
        sut = try decodeWebViewMessage(LongTaskMessage.self, from: json)
    }

    func whenMakingLoggingAction() -> WebViewLoggingAction? {
        sut.makeLoggingAction(context: .empty)
    }
}
