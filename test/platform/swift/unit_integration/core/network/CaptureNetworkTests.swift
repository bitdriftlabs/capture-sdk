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
    func testHappyPathWithTimeoutAndReconnect() async throws {
        _ = try setUp(networkIdleTimeout: 1)

        let server = try XCTUnwrap(self.testServer)
        let streamID = server.awaitNextStream()
        XCTAssertNotEqual(streamID, -1)

        XCTAssertTrue(server.awaitStreamClosed(streamId: streamID, waitTimeMs: 3000))

        let streamID2 = server.awaitNextStream()
        XCTAssertNotEqual(streamID2, -1)
        server.awaitHandshake(streamId: streamID2)
    }

    func testHappyPathWithKeepAlives() async throws {
        _ = try setUp(networkIdleTimeout: 1, pingIntervalMs: 100)

        let server = try XCTUnwrap(self.testServer)
        let streamID = server.awaitNextStream()
        XCTAssertNotEqual(streamID, -1)
        server.awaitHandshake(streamId: streamID)

        XCTAssertFalse(server.awaitStreamClosed(streamId: streamID, waitTimeMs: 1500))
    }

    func testAggressiveNetworkTraffic() throws {
        let loggerID = try setUp(networkIdleTimeout: 10)

        let server = try XCTUnwrap(self.testServer)
        server.runAggressiveUploadTest(loggerId: loggerID)
    }

    func testLargeUpload() throws {
        let loggerID = try setUp(networkIdleTimeout: 10)

        let server = try XCTUnwrap(self.testServer)
        XCTAssertTrue(server.runLargeUploadTest(loggerId: loggerID))
    }

    func testAggressiveNetworkTrafficWithStreamDrops() throws {
        // Use a shorter timeout so the client detects stream closure faster and reconnects
        // within the server's 5-second blocking_next_stream timeout.
        let loggerID = try setUp(networkIdleTimeout: 2)

        let server = try XCTUnwrap(self.testServer)
        XCTAssertTrue(server.runAggressiveUploadWithStreamDrops(loggerId: loggerID))
    }
}
