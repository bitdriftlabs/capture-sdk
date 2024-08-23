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

        let streamID = try await nextApiStream()

        // Server timeout is 1s in tests, waiting up to 3s for the stream to be closed.
        try await serverStreamClosed(streamID, 3000)

        let streamID2 = try await nextApiStream()
        try await serverReceivedHandshake(streamID2)
    }

    // Verifies that we can extend the stream beyond the idle timeout via keep alive pings.
    func testHappyPathWithKeepAlives() async throws {
        _ = try setUp(networkIdleTimeout: 1, pingIntervalMs: 100)

        let streamID = try await nextApiStream()
        try await serverReceivedHandshake(streamID)

        // Server timeout is 1s in tests, waiting up to 1.5s. This would have closed the
        // stream if not for the keep alives (see above test).
        // XCTAssertThrowsError doesn't work with async, so do this manual assertion that the call to wait
        // for a stream close times out.
        do {
            try await serverStreamClosed(streamID, 1500)

            // If we didn't catch the exception we saw a close before the timeout, which means something is
            // wrong.
            XCTFail("stream closed unexpectedly")
        } catch {}
    }

    // A wrapper around the test scenario described by the Rust test helper: we configure a constant
    // stream of keep alives and a constant stream of uploads to verify the behavior of the
    // networking implementation when there is a lot of traffic.
    func testAggressiveNetworkTraffic() throws {
        // First off we configure the test server with a ping interval at 0, i.e. constantly.
        let loggerID = try setUp(networkIdleTimeout: 10)
        // Secondly, invoke a test scenario in which we upload logs constantly.
        run_aggressive_upload_test(loggerID)
    }

    func testLargeUpload() throws {
        let loggerID = try setUp(networkIdleTimeout: 10)

        run_large_upload_test(loggerID)
    }

    func testAggressiveNetworkTrafficWithStreamDrops() throws {
        // First off we configure the test server with a ping interval at 0, i.e. constantly.
        let loggerID = try setUp(networkIdleTimeout: 10)
        // Secondly, invoke a test scenario in which we upload logs constantly.
        run_aggressive_upload_test_with_stream_drops(loggerID)
    }
}
