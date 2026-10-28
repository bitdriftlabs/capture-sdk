// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI) @testable import Capture
import CaptureMocks
@testable import CaptureTestBridge
import XCTest

final class SessionReplayControllerTests: XCTestCase {
    private var target: Capture.SessionReplayController!
    private var logger: MockCoreLogging!

    override func setUp() {
        super.setUp()

        self.logger = MockCoreLogging()

        self.target = SessionReplayController(configuration: .init())
        self.target.logger = self.logger
    }

    func testSessionReplayControllerDoesNotCrash() {
        run_session_replay_target_test(self.target)
    }

    func testEmitsSessionReplayScreenLog() {
        let expectation = self.expectation(description: "screen log is emitted")

        self.logger.logSessionReplayScreenExpectation = expectation
        self.target.captureScreen()

        XCTAssertEqual(.completed, XCTWaiter().wait(for: [expectation], timeout: 1))
        XCTAssertEqual(1, self.logger.sessionReplayScreenLogs.count)
    }

    func testCommandScreenshotDefersPayloadValidationToRust() async throws {
        let jpeg = Data([0xff, 0xd8, 1, 0xff, 0xd9])
        for bytes in [jpeg, nil, Data(), Data([1, 2, 3, 4]), Data(repeating: 0, count: 2 * 1024 * 1024 + 1)] {
            let started = expectation(description: "capture started")
            let capture = ControlledScreenshot()
            let target = SessionReplayController(configuration: .init(), captureScreenshot: {
                capture.start($0)
                started.fulfill()
            })
            let task = Task { await target.captureCommandScreenshot() }
            await fulfillment(of: [started])
            capture.finish(bytes)
            let result = await task.value
            if let bytes, !bytes.isEmpty {
                let attachment = try XCTUnwrap(result.get().attachment)
                XCTAssertEqual(attachment.data, bytes)
                XCTAssertEqual(attachment.mimeType, "image/jpeg")
            } else {
                guard case let .failure(error) = result else { return XCTFail("expected invalid screenshot") }
                XCTAssertEqual(error.title, "invalid or unavailable screenshot")
            }
        }
    }

    func testCancelledScreenshotKeepsPhysicalLatchUntilItsCallback() async {
        let firstStarted = expectation(description: "first capture started")
        let secondStarted = expectation(description: "second capture started")
        let capture = ControlledScreenshot()
        let target = SessionReplayController(configuration: .init(), captureScreenshot: {
            capture.start($0)
            if capture.count == 1 { firstStarted.fulfill() } else { secondStarted.fulfill() }
        })
        let first = Task { await target.captureCommandScreenshot() }
        await fulfillment(of: [firstStarted])
        first.cancel()
        let busy = await target.captureCommandScreenshot()
        guard case let .failure(error) = busy else { return XCTFail("expected busy screenshot") }
        XCTAssertEqual(error.title, "screenshot capture already in progress")
        XCTAssertEqual(capture.count, 1)

        capture.finish(nil)
        _ = await first.value
        let second = Task { await target.captureCommandScreenshot() }
        await fulfillment(of: [secondStarted])
        XCTAssertEqual(capture.count, 2)
        // Minimal marker-only JPEG: two-byte SOI (FF D8) followed by two-byte EOI (FF D9).
        capture.finish(Data([0xff, 0xd8, 0xff, 0xd9]))
        let result = await second.value
        guard case .success = result else { return XCTFail("expected next screenshot to succeed") }
    }
}

private final class ControlledScreenshot: @unchecked Sendable {
    private let lock = NSLock()
    private var completion: (@Sendable (Data?) -> Void)?
    private var captures = 0

    var count: Int {
        lock.lock()
        defer { lock.unlock() }
        return captures
    }

    func start(_ completion: @escaping @Sendable (Data?) -> Void) {
        lock.lock()
        defer { lock.unlock() }
        captures += 1
        self.completion = completion
    }

    func finish(_ data: Data?) {
        lock.lock()
        let completion = self.completion
        self.completion = nil
        lock.unlock()
        completion?(data)
    }
}
