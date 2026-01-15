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

open class BaseNetworkingTestCase: XCTestCase {
    // swiftlint:disable test_case_accessibility
    private(set) var network: Network?
    private(set) var loggerBridge: LoggerBridge?
    private(set) var sdkDirectory: URL?
    private(set) var testServer: TestApiServer?

    private final class MockMetadataProvider: CaptureLoggerBridge.MetadataProvider {
        func timestamp() -> TimeInterval {
            return Date(timeIntervalSince1970: 1_666_807_001.52005815).timeIntervalSince1970
        }

        func ootbFields() -> [Field] {
            return []
        }

        func customFields() -> [Field] {
            return []
        }
    }

    private final class MockResourceUtilizationTarget: CaptureLoggerBridge.ResourceUtilizationTarget {
        func tick() {}
    }

    private final class MockSessionReplayTarget: CaptureLoggerBridge.SessionReplayTarget {
        func captureScreen() {}
        func captureScreenshot() {}
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

    // swiftlint:disable:next test_case_accessibility
    func setUp(networkIdleTimeout: TimeInterval, pingIntervalMs: Int32 = -1) throws -> Int64 {
        let server = TestApiServer(tls: true, pingIntervalMs: pingIntervalMs)
        self.testServer = server

        let sdkDirectory = self.setUpSDKDirectory()
        let network = URLSessionNetworkClient(apiBaseURL: server.baseURL, timeout: networkIdleTimeout)

        self.network = network

        let loggerBridge = try XCTUnwrap(
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

        self.loggerBridge = loggerBridge

        return loggerBridge.loggerID
    }

    override public func tearDown() {
        super.tearDown()

        self.loggerBridge = nil
        self.testServer = nil
    }
}
