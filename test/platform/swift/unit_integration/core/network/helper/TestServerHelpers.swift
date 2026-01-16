// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import CaptureTestBridge
import Foundation

public final class TestApiServer: @unchecked Sendable {
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

    /// Waits for up to 5 seconds to receive a log upload, returning the log details.
    ///
    /// - returns: The log details, or nil on timeout.
    public func nextUploadedLog() -> UploadedLog? {
        let log = UploadedLog()
        guard server_instance_next_uploaded_log(self.handle, log) else {
            return nil
        }
        return log
    }

    // MARK: - Async wrappers for use in async test contexts

    /// Async wrapper for awaitNextStream that runs the blocking operation on a background thread.
    ///
    /// - returns: The stream ID, or -1 on timeout.
    public func awaitNextStreamAsync() async -> Int32 {
        await withCheckedContinuation { continuation in
            DispatchQueue.global().async {
                let streamId = self.awaitNextStream()
                continuation.resume(returning: streamId)
            }
        }
    }

    /// Async wrapper for configureAggressiveUploads that runs on a background thread.
    ///
    /// - parameter streamId: The stream ID to configure.
    public func configureAggressiveUploadsAsync(streamId: Int32) async {
        await withCheckedContinuation { continuation in
            DispatchQueue.global().async {
                self.configureAggressiveUploads(streamId: streamId)
                continuation.resume()
            }
        }
    }

    /// Async wrapper for nextUploadedLog that runs on a background thread.
    ///
    /// - returns: The uploaded log, or nil on timeout.
    public func nextUploadedLogAsync() async -> UploadedLog? {
        await withCheckedContinuation { continuation in
            DispatchQueue.global().async {
                let log = self.nextUploadedLog()
                continuation.resume(returning: log)
            }
        }
    }
}
