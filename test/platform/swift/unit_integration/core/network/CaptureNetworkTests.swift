// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureTestBridge
import Foundation
import XCTest

final class CaptureNetworkTests: XCTestCase {
    // Verifies that the URLSession client times out the stream after some time when keep alives
    // (pings) are not configured. We set the timeout low, then wait for it to hit and verify that
    // we see the stream re-established afterwards.
    func testHappyPathWithTimeoutAndReconnect() async throws {
        let env = try LoggerTestFixture()

        let streamID = await env.testServer.nextStream()
        XCTAssertNotEqual(streamID, -1)

        // Server timeout is 1s in tests, waiting up to 3s for the stream to be closed.
        let streamClosed = await env.testServer.streamClosed(streamId: streamID, waitTimeMs: 3000)
        XCTAssertTrue(streamClosed)

        let streamID2 = await env.testServer.nextStream()
        XCTAssertNotEqual(streamID2, -1)
        await env.testServer.handshake(streamId: streamID2)
    }

    // Verifies that we can extend the stream beyond the idle timeout via keep alive pings.
    func testHappyPathWithKeepAlives() async throws {
        let env = try LoggerTestFixture(pingInterval: 0.1)

        let streamID = await env.testServer.nextStream()
        XCTAssertNotEqual(streamID, -1)
        await env.testServer.handshake(streamId: streamID)

        // Server timeout is 1s in tests, waiting up to 1.5s. This would have closed the
        // stream if not for the keep alives (see above test).
        let streamClosed = await env.testServer.streamClosed(streamId: streamID, waitTimeMs: 1500)
        XCTAssertFalse(streamClosed)
    }

    // A wrapper around the test scenario described by the Rust test helper: we configure a constant
    // stream of uploads to verify the behavior of the networking implementation when there is a
    // lot of traffic.
    func testAggressiveNetworkTraffic() async throws {
        let env = try LoggerTestFixture(networkIdleTimeout: 10)

        await env.testServer.runAggressiveUploadTest(loggerId: env.loggerID)
    }

    func testLargeUpload() async throws {
        let env = try LoggerTestFixture(networkIdleTimeout: 10)

        let passed = await env.testServer.runLargeUploadTest(loggerId: env.loggerID)
        XCTAssertTrue(passed)
    }

    func testAggressiveNetworkTrafficWithStreamDrops() async throws {
        let env = try LoggerTestFixture()

        let passed = await env.testServer.runAggressiveUploadWithStreamDrops(loggerId: env.loggerID)
        XCTAssertTrue(passed)
    }
}
