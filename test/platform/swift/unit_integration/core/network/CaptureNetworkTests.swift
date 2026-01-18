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

final class CaptureNetworkTests: BaseNetworkingTestCase {
    // Verifies that the URLSession client times out the stream after some time when keep alives
    // (pings) are not configured. We set the timeout low, then wait for it to hit and verify that
    // we see the stream re-established afterwards.
    func testHappyPathWithTimeoutAndReconnect() async throws {
        _ = try setUp(networkIdleTimeout: 1)

        let server = try XCTUnwrap(self.testServer)
        let streamID = await server.nextStream()
        XCTAssertNotEqual(streamID, -1)

        // Server timeout is 1s in tests, waiting up to 3s for the stream to be closed.
        let streamClosed = await server.streamClosed(streamId: streamID, waitTimeMs: 3000)
        XCTAssertTrue(streamClosed)

        let streamID2 = await server.nextStream()
        XCTAssertNotEqual(streamID2, -1)
        await server.handshake(streamId: streamID2)
    }

    // Verifies that we can extend the stream beyond the idle timeout via keep alive pings.
    func testHappyPathWithKeepAlives() async throws {
        _ = try setUp(networkIdleTimeout: 1, pingIntervalMs: 100)

        let server = try XCTUnwrap(self.testServer)
        let streamID = await server.nextStream()
        XCTAssertNotEqual(streamID, -1)
        await server.handshake(streamId: streamID)

        // Server timeout is 1s in tests, waiting up to 1.5s. This would have closed the
        // stream if not for the keep alives (see above test).
        let streamClosed = await server.streamClosed(streamId: streamID, waitTimeMs: 1500)
        XCTAssertFalse(streamClosed)
    }

    // A wrapper around the test scenario described by the Rust test helper: we configure a constant
    // stream of uploads to verify the behavior of the networking implementation when there is a
    // lot of traffic.
    func testAggressiveNetworkTraffic() async throws {
        let loggerID = try setUp(networkIdleTimeout: 10)

        let server = try XCTUnwrap(self.testServer)
        await server.runAggressiveUploadTest(loggerId: loggerID)
    }

    func testLargeUpload() async throws {
        let loggerID = try setUp(networkIdleTimeout: 10)

        let server = try XCTUnwrap(self.testServer)
        let passed = await server.runLargeUploadTest(loggerId: loggerID)
        XCTAssertTrue(passed)
    }

    func testAggressiveNetworkTrafficWithStreamDrops() async throws {
        // Use a shorter timeout so the client detects stream closure faster and reconnects
        // within the server's 5-second blocking_next_stream timeout.
        let loggerID = try setUp(networkIdleTimeout: 2)

        let server = try XCTUnwrap(self.testServer)
        let passed = await server.runAggressiveUploadWithStreamDrops(loggerId: loggerID)
        XCTAssertTrue(passed)
    }
}
