// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import CaptureTestBridge
import Foundation

public final class TestApiServer {
    private let handle: UnsafeMutableRawPointer
    public let port: Int32

    public init(tls: Bool = true, pingIntervalMs: Int32 = -1) {
        self.handle = create_test_api_server_instance(tls, pingIntervalMs)
        self.port = server_instance_port(self.handle)
    }

    deinit {
        destroy_test_api_server_instance(self.handle)
    }

    public var baseURL: URL {
        URL(string: "https://localhost:\(port)")!
    }

    public func awaitNextStream() -> Int32 {
        return server_instance_await_next_stream(self.handle)
    }

    public func waitForHandshake(streamId: Int32) {
        server_instance_wait_for_handshake(self.handle, streamId)
    }

    public func awaitHandshake(streamId: Int32) {
        server_instance_await_handshake(self.handle, streamId)
    }

    public func awaitStreamClosed(streamId: Int32, waitTimeMs: Int64) -> Bool {
        return server_instance_await_stream_closed(self.handle, streamId, waitTimeMs)
    }

    public func sendConfiguration(streamId: Int32) {
        server_instance_send_configuration(self.handle, streamId)
    }

    public func awaitConfigurationAck(streamId: Int32) {
        server_instance_await_configuration_ack(self.handle, streamId)
    }

    public func configureAggressiveUploads(streamId: Int32) {
        server_instance_configure_aggressive_uploads(self.handle, streamId)
    }

    public func runAggressiveUploadTest(loggerId: Int64) {
        server_instance_run_aggressive_upload_test(self.handle, loggerId)
    }

    public func runLargeUploadTest(loggerId: Int64) -> Bool {
        return server_instance_run_large_upload_test(self.handle, loggerId)
    }

    public func runAggressiveUploadWithStreamDrops(loggerId: Int64) -> Bool {
        return server_instance_run_aggressive_upload_with_stream_drops(self.handle, loggerId)
    }
}

public func nextApiStream() async throws -> Int32 {
    return try await withCheckedThrowingContinuation { continuation in
        next_test_api_stream(ContinuationWrapper(continuation: continuation))
    }
}

public func serverReceivedHandshake(_ streamId: Int32) async throws {
    _ = try await withCheckedThrowingContinuation { continuation in
        test_stream_received_handshake(streamId, ContinuationWrapper(continuation: continuation))
    }
}

public func serverStreamClosed(_ streamId: Int32, _ waitTime: UInt64) async throws {
    _ = try await withCheckedThrowingContinuation { continuation in
        test_stream_closed(streamId, waitTime, ContinuationWrapper(continuation: continuation))
    }
}
