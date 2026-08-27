// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureLoggerBridge
import CaptureMocks
import CaptureTestBridge
import Foundation
import XCTest

/// Provides an isolated test environment for integration tests requiring a logger bridge and test server.
/// Each instance creates its own test server, network client, and logger bridge.
final class NetworkTestEnvironment {
    let testServer: TestApiServer
    let network: Network
    let loggerBridge: LoggerBridge
    let sdkDirectory: URL
    private let networkDiagnostics: NetworkDiagnostics

    var loggerID: Int64 { loggerBridge.loggerID }

    /// Creates a new test environment with isolated infrastructure.
    ///
    /// - parameter networkIdleTimeout: The timeout used for URLSessionNetworkClient. Defaults to 1 second.
    /// - parameter pingInterval:       The ping interval to configure the logger with. Defaults to `nil` (disabled).
    init(networkIdleTimeout: TimeInterval = 1, pingInterval: TimeInterval? = nil) throws {
        // The logger receives the ping interval to use in its handshake when it connects to the
        // server, so we pass the ping interval to the test server.
        let pingIntervalMs = pingInterval.map { Int32($0 * 1000) } ?? -1
        self.testServer = try TestApiServer(tls: true, pingIntervalMs: pingIntervalMs)

        // For each test we create a unique SDK directory to avoid different tests affecting each
        // other.
        self.sdkDirectory = Self.makeSDKDirectory()

        let network = URLSessionNetworkClient(
            apiBaseURL: testServer.baseURL,
            timeout: networkIdleTimeout,
            delegateQueue: .global(qos: .userInteractive)
        )
        let networkDiagnostics = NetworkDiagnostics()
        self.networkDiagnostics = networkDiagnostics
        self.network = RecordingNetwork(network: network, diagnostics: networkDiagnostics)
        self.testServer.setFailureDiagnostics { [weak networkDiagnostics] in
            networkDiagnostics?.description ?? "network diagnostics unavailable"
        }

        self.loggerBridge = try XCTUnwrap(
            LoggerBridge(
                apiKey: "test!",
                bufferDirectoryPath: sdkDirectory.path,
                sessionStrategy: .configuration(.init()),
                metadataProvider: MockMetadataProvider(),
                resourceUtilizationTarget: MockResourceUtilizationTarget(),
                sessionReplayTarget: MockSessionReplayTarget(),
                eventsListenerTarget: MockEventsListenerTarget(),
                appID: "io.bitdrift.capture.test",
                releaseVersion: "",
                buildNumber: "",
                osVersion: "",
                model: "",
                network: network,
                errorReporting: MockRemoteErrorReporter(),
                sleepMode: .disabled,
                initialFields: [],
                issueCallbackConfiguration: nil
            )
        )

        loggerBridge.start()
    }

    deinit {
        // Ensure logger fully shuts down before releasing to avoid test interference
        loggerBridge.enableBlockingShutdown()
    }

    /// Creates a unique SDK directory for test isolation.
    ///
    /// - returns: The URL of the created directory.
    static func makeSDKDirectory() -> URL {
        let testUUID = UUID().uuidString
        let sdkDirectory = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent(testUUID)

        if !FileManager.default.fileExists(atPath: sdkDirectory.path) {
            try? FileManager.default.createDirectory(
                at: sdkDirectory,
                withIntermediateDirectories: true
            )
        }

        // This runs before the first API stream is created, so keep retries short
        // enough to not exceed the test timeout.
        create_fast_retry_configuration(sdkDirectory.path)

        return sdkDirectory
    }

    // MARK: - Private Mock Types

    private final class MockMetadataProvider: CaptureLoggerBridge.MetadataProvider {
        func timestamp() -> TimeInterval {
            // Matches "2022-10-26T17:56:41.520058155Z" when formatted.
            Date(timeIntervalSince1970: 1_666_807_001.52005815).timeIntervalSince1970
        }

        func ootbFields() -> [Field] {
            []
        }

        func customFields() -> [Field] {
            []
        }
    }

    private final class MockResourceUtilizationTarget: CaptureLoggerBridge.ResourceUtilizationTarget {
        func tick() {}
    }

    private final class MockSessionReplayTarget: CaptureLoggerBridge.SessionReplayTarget {
        func captureScreen() {}
        func captureScreenshot() {}
    }

    private final class RecordingNetwork: Network {
        private let network: Network
        private let diagnostics: NetworkDiagnostics

        init(network: Network, diagnostics: NetworkDiagnostics) {
            self.network = network
            self.diagnostics = diagnostics
        }

        func startStream(_ streamId: UInt, headers: [String: String]) -> NetworkStream {
            self.diagnostics.recordStart(streamID: streamId, headerCount: headers.count)
            return RecordingNetworkStream(
                stream: self.network.startStream(streamId, headers: headers),
                streamID: streamId,
                diagnostics: self.diagnostics
            )
        }
    }

    private final class RecordingNetworkStream: NetworkStream {
        private let stream: NetworkStream
        private let streamID: UInt
        private let diagnostics: NetworkDiagnostics

        init(stream: NetworkStream, streamID: UInt, diagnostics: NetworkDiagnostics) {
            self.stream = stream
            self.streamID = streamID
            self.diagnostics = diagnostics
        }

        func sendData(_ baseAddress: UnsafePointer<UInt8>, count: Int) -> Int {
            let result = self.stream.sendData(baseAddress, count: count)
            self.diagnostics.recordWrite(streamID: self.streamID, byteCount: count, result: result)
            return result
        }

        func shutdown() {
            self.diagnostics.recordShutdown(streamID: self.streamID)
            self.stream.shutdown()
        }
    }

    private final class NetworkDiagnostics: @unchecked Sendable {
        private let lock = NSLock()
        private var events = [String]()

        func recordStart(streamID: UInt, headerCount: Int) {
            self.record("startStream(id=\(streamID), headers=\(headerCount))")
        }

        func recordWrite(streamID: UInt, byteCount: Int, result: Int) {
            self.record("sendData(id=\(streamID), bytes=\(byteCount), result=\(result))")
        }

        func recordShutdown(streamID: UInt) {
            self.record("shutdown(id=\(streamID))")
        }

        var description: String {
            self.lock.lock()
            defer { self.lock.unlock() }
            return self.events.isEmpty
                ? "no Network calls observed" : self.events.joined(separator: " -> ")
        }

        private func record(_ event: String) {
            self.lock.lock()
            defer { self.lock.unlock() }
            self.events.append(event)
        }
    }
}
