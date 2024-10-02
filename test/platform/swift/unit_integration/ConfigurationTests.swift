// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
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
            sessionStrategy: .fixed(),
            configuration: .init(sessionReplayConfiguration: nil)
        )

        XCTAssertNotNil(Logger.getShared())
    }

    func testConfigurationDefault() throws {
        Logger.start(
            withAPIKey: "api_key",
            sessionStrategy: .fixed(),
            configuration: .init()
        )

        XCTAssertNotNil(Logger.getShared())
    }

    func testConfigurationFailure() {
        let factory = MockLoggerBridgingFactory(logger: nil)

        Logger.start(
            withAPIKey: "test",
            sessionStrategy: .fixed(),
            configuration: .init(),
            fieldProviders: [],
            dateProvider: nil,
            apiURL: URL(staticString: "https://api.bitdrift.io"),
            loggerBridgingFactoryProvider: factory
        )

        XCTAssertEqual(1, factory.makeLoggerCallsCount)
        XCTAssertNil(Logger.shared)

        Logger.start(
            withAPIKey: "test",
            sessionStrategy: .fixed(),
            configuration: .init(),
            fieldProviders: [],
            dateProvider: nil,
            apiURL: URL(staticString: "https://api.bitdrift.io"),
            loggerBridgingFactoryProvider: factory
        )

        XCTAssertEqual(1, factory.makeLoggerCallsCount)
        XCTAssertNil(Logger.shared)
    }
}
