// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import CaptureTestBridge
import Foundation

/// Error type for test server failures, providing descriptive error messages.
public struct TestServerError: Error, CustomStringConvertible {
    public let message: String

    public init(_ message: String) {
        self.message = message
    }

    public var description: String { message }
}

private func checkError(_ errorPtr: UnsafePointer<CChar>?) throws {
    if let ptr = errorPtr {
        let message = String(cString: ptr)
        test_helpers_free_string(UnsafeMutablePointer(mutating: ptr))
        throw TestServerError(message)
    }
}

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

    // MARK: - Public async API

    /// Waits for the next API stream connection.
    ///
    /// - returns: The stream ID, or -1 on timeout.
    public func nextStream() async -> Int32 {
        await withCheckedContinuation { continuation in
            DispatchQueue.global().async {
                continuation.resume(returning: server_instance_await_next_stream(self.handle))
            }
        }
    }

    /// Waits for handshake completion on the given stream.
    ///
    /// - parameter streamId: The stream ID.
    ///
    /// - throws: `TestServerError` if the handshake times out.
    public func handshake(streamId: Int32) async throws {
        let errorPtr = await withCheckedContinuation { continuation in
            DispatchQueue.global().async {
                continuation.resume(returning: server_instance_await_handshake(self.handle, streamId))
            }
        }
        try checkError(errorPtr)
    }

    /// Waits for stream to close.
    ///
    /// - parameter streamId:   The stream ID.
    /// - parameter waitTimeMs: The timeout in milliseconds.
    ///
    /// - returns: True if stream closed within the timeout.
    public func streamClosed(streamId: Int32, waitTimeMs: Int64) async -> Bool {
        await withCheckedContinuation { continuation in
            DispatchQueue.global().async {
                continuation.resume(returning: server_instance_await_stream_closed(self.handle, streamId, waitTimeMs))
            }
        }
    }

    /// Configures aggressive uploads on the given stream.
    ///
    /// - parameter streamId: The stream ID.
    ///
    /// - throws: `TestServerError` if the configuration fails.
    public func configureAggressiveUploads(streamId: Int32) async throws {
        let errorPtr = await withCheckedContinuation { continuation in
            DispatchQueue.global().async {
                continuation.resume(returning: server_instance_configure_aggressive_uploads(self.handle, streamId))
            }
        }
        try checkError(errorPtr)
    }

    /// Runs the large upload test.
    ///
    /// - parameter loggerId: The logger ID.
    ///
    /// - throws: `TestServerError` if the test fails.
    public func runLargeUploadTest(loggerId: Int64) async throws {
        let errorPtr = await withCheckedContinuation { continuation in
            DispatchQueue.global().async {
                continuation.resume(
                    returning: server_instance_run_large_upload_test(self.handle, loggerId)
                )
            }
        }

        if let ptr = errorPtr {
            let message = String(cString: ptr)
            test_helpers_free_string(UnsafeMutablePointer(mutating: ptr))
            throw TestServerError(message)
        }
    }

    /// Waits for up to 5 seconds to receive a log upload.
    ///
    /// - returns: The uploaded log, or nil on timeout.
    public func nextUploadedLog() async -> UploadedLog? {
        await withCheckedContinuation { continuation in
            DispatchQueue.global().async {
                let log = UploadedLog()
                guard server_instance_next_uploaded_log(self.handle, log) else {
                    continuation.resume(returning: nil)
                    return
                }
                continuation.resume(returning: log)
            }
        }
    }

    /// Drains logs until finding one matching the predicate, or returns nil after max attempts.
    ///
    /// - parameter maxAttempts: Maximum number of logs to check before giving up.
    /// - parameter predicate:   A closure that returns true for the desired log.
    ///
    /// - returns: The first log matching the predicate, or nil if not found.
    public func nextUploadedLogMatching(
        maxAttempts: Int = 20,
        _ predicate: @escaping (UploadedLog) -> Bool
    ) async -> UploadedLog? {
        for _ in 0..<maxAttempts {
            guard let log = await nextUploadedLog() else {
                return nil
            }
            if predicate(log) {
                return log
            }
        }
        return nil
    }

    /// Drains logs until all predicates have been satisfied at least once.
    ///
    /// - parameter maxAttempts: Maximum number of logs to check before giving up.
    /// - parameter predicates:  Closures that return true for the desired logs.
    ///
    /// - returns: The logs that matched each predicate, in the order predicates were satisfied.
    public func collectLogsMatching(
        maxAttempts: Int = 20,
        _ predicates: [(UploadedLog) -> Bool]
    ) async -> [UploadedLog] {
        var satisfied = Array(repeating: false, count: predicates.count)
        var matchedLogs: [UploadedLog] = []

        for _ in 0..<maxAttempts {
            guard let log = await nextUploadedLog() else { break }

            for (i, predicate) in predicates.enumerated() where !satisfied[i] {
                if predicate(log) {
                    satisfied[i] = true
                    matchedLogs.append(log)
                    break
                }
            }

            if satisfied.allSatisfy({ $0 }) { break }
        }
        return matchedLogs
    }
}
