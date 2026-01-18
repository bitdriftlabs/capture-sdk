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

/// Provides isolated test infrastructure for integration tests requiring a logger and test server.
/// Each instance creates its own test server, network client, and logger.
final class LoggerTestFixture {
    let testServer: TestApiServer
    let network: Network
    let loggerBridge: LoggerBridge
    let sdkDirectory: URL

    var loggerID: Int64 { loggerBridge.loggerID }

    /// Creates a new test fixture with isolated infrastructure.
    ///
    /// - Parameters:
    ///   - networkIdleTimeout: The timeout used for URLSessionNetworkClient. Defaults to 1 second.
    ///   - pingInterval: The ping interval to configure the logger with. Defaults to `nil` (disabled).
    init(networkIdleTimeout: TimeInterval = 1, pingInterval: TimeInterval? = nil) throws {
        // The logger receives the ping interval to use in its handshake when it connects to the
        // server, so we pass the ping interval to the test server.
        let pingIntervalMs = pingInterval.map { Int32($0 * 1000) } ?? -1
        self.testServer = TestApiServer(tls: true, pingIntervalMs: pingIntervalMs)

        // For each test we create a unique SDK directory to avoid different tests affecting each
        // other.
        self.sdkDirectory = Self.makeSDKDirectory()

        self.network = URLSessionNetworkClient(
            apiBaseURL: testServer.baseURL,
            timeout: networkIdleTimeout
        )

        self.loggerBridge = try XCTUnwrap(
            LoggerBridge(
                apiKey: "test!",
                bufferDirectoryPath: sdkDirectory.path,
                sessionStrategy: .fixed(),
                metadataProvider: MockMetadataProvider(),
                resourceUtilizationTarget: MockResourceUtilizationTarget(),
                sessionReplayTarget: MockSessionReplayTarget(),
                eventsListenerTarget: MockEventsListenerTarget(),
                appID: "io.bitdrift.capture.test",
                releaseVersion: "",
                model: "",
                network: network,
                errorReporting: MockRemoteErrorReporter(),
                sleepMode: .disabled
            )
        )

        loggerBridge.start()
    }

    deinit {
        // Resources are released when the instance goes out of scope.
        // The test server shuts down after the logger bridge is released.
    }

    /// Creates a unique SDK directory for test isolation.
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
}
