// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import Foundation
import XCTest

final class ConfigurationTests: XCTestCase {
    override func setUp() async throws {
        try await super.setUp()

        Storage.shared.clear()
    }

    override func tearDown() async throws {
        try await super.tearDown()

        Logger.resetShared()
        Storage.shared.clear()
    }

    func testConfigurationSimple() throws {
        Logger.start(
            withAPIKey: "api_key",
            sessionStrategy: .fixed()
        )

        XCTAssertNotNil(Logger.getShared())
    }

    func testConfigurationDefault() throws {
        Logger.start(
            withAPIKey: "api_key",
            sessionStrategy: .fixed()
        )

        XCTAssertNotNil(Logger.getShared())
    }

    func testConfigurationDefaultValues() {
        let config = Configuration()
        XCTAssertEqual(config.sleepMode, SleepMode.disabled)
    }

    func testLoggerRootPath() throws {
        let tempDir = URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            .appendingPathComponent(UUID().uuidString)

        XCTAssertFalse(FileManager.default.fileExists(atPath: tempDir.path))

        let logger = Logger.start(
            withAPIKey: "test",
            sessionStrategy: .fixed(),
            configuration: .init(rootFileURL: tempDir)
        )

        XCTAssertNotNil(logger)
        XCTAssertNotNil(Logger.shared)

        // Ensure the given path was created during initialization
        XCTAssertTrue(FileManager.default.fileExists(atPath: tempDir.path))
    }

    func testConfigurationFailure() {
        let factory = MockLoggerBridgingFactory(logger: nil)

        Logger.start(
            withAPIKey: "test",
            sessionStrategy: .fixed(),
            configuration: .init(apiURL: URL(staticString: "https://api.bitdrift.io")),
            fieldProviders: [],
            dateProvider: nil,
            loggerBridgingFactoryProvider: factory
        )

        XCTAssertEqual(1, factory.makeLoggerCallsCount)
        XCTAssertNil(Logger.shared)

        Logger.start(
            withAPIKey: "test",
            sessionStrategy: .fixed(),
            configuration: .init(apiURL: URL(staticString: "https://api.bitdrift.io")),
            fieldProviders: [],
            dateProvider: nil,
            loggerBridgingFactoryProvider: factory
        )

        XCTAssertEqual(1, factory.makeLoggerCallsCount)
        XCTAssertNil(Logger.shared)
    }
}
