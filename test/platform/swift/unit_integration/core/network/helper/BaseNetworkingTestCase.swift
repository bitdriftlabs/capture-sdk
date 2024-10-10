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

// Base class for tests that wants to initialize a test server
open class BaseNetworkingTestCase: XCTestCase {
    // swiftlint:disable test_case_accessibility
    private(set) var network: Network?
    private(set) var loggerBridge: LoggerBridge?
    private(set) var sdkDirectory: URL?
    private(set) var testServerStarted = false

    private class MockMetadataProvider: CaptureLoggerBridge.MetadataProvider {
        func timestamp() -> TimeInterval {
            // Matches "2022-10-26T17:56:41.520058155Z" when formatted.
            return Date(timeIntervalSince1970: 1_666_807_001.52005815).timeIntervalSince1970
        }

        func ootbFields() -> [Field] {
            return []
        }

        func customFields() -> [Field] {
            return []
        }
    }

    private class MockResourceUtilizationTarget: CaptureLoggerBridge.ResourceUtilizationTarget {
        func tick() {}
    }

    // swiftlint:disable:next test_case_accessibility
    func setUpSDKDirectory() -> URL {
        let testUUID = UUID().uuidString

        let sdkDirectory = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent(testUUID)
        do {
            if !FileManager.default.fileExists(atPath: sdkDirectory.path) {
                try FileManager.default.createDirectory(at: sdkDirectory,
                                                        withIntermediateDirectories: true)
            }
        } catch {
            XCTFail("failed to create test directory")
        }

        return sdkDirectory
    }

    // Configures the logger for test, specifying the timeout used for URLSessionNetworkClient and the ping
    // interval to configure the logger with.
    // swiftlint:disable:next test_case_accessibility
    func setUp(networkIdleTimeout: TimeInterval, pingIntervalMs: Int32 = -1) throws -> Int64 {
        let apiBaseURL = self.setUpTestServer(pingIntervalMs: pingIntervalMs)
        // For each test we create a unique SDK directory in order to avoid different tests affecting each
        // other.
        let sdkDirectory = self.setUpSDKDirectory()
        let network = URLSessionNetworkClient(apiBaseURL: apiBaseURL, timeout: networkIdleTimeout)

        self.network = network

        let loggerBridge = try XCTUnwrap(
            LoggerBridge(
                apiKey: "test!",
                bufferDirectoryPath: sdkDirectory.path,
                sessionStrategy: .fixed(),
                metadataProvider: MockMetadataProvider(),
                resourceUtilizationTarget: MockResourceUtilizationTarget(),
                eventsListenerTarget: MockEventsListenerTarget(),
                appID: "io.bitdrift.capture.test",
                releaseVersion: "",
                network: network,
                errorReporting: MockRemoteErrorReporter()
            )
        )

        loggerBridge.start()

        self.loggerBridge = loggerBridge

        return loggerBridge.loggerID
    }

    // swiftlint:disable:next test_case_accessibility
    func setUpTestServer(pingIntervalMs: Int32 = -1) -> URL {
        // The logger receives the ping interval to use in its handshake when it connects to the server,
        // so we pass the ping interval to the test server.
        let port = start_test_api_server(true, pingIntervalMs)
        self.testServerStarted = true
        // swiftlint:disable:next force_unwrapping
        return URL(string: "https://localhost:\(port)")!
    }

    override public func tearDown() {
        super.tearDown()

        self.loggerBridge = nil
        // Shut down the server after the logger to avoid streams being torn down.
        if self.testServerStarted {
            stop_test_api_server()
        }
    }
}
