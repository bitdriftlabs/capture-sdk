// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class LoggerSharedTests: XCTestCase {
    override func setUp() {
        super.setUp()
        Logger.resetShared(logger: MockLogging())
    }

    override func tearDown() {
        super.tearDown()
        Logger.resetShared()
    }

    func testIntegrationsAreEnabledOnlyOnce() throws {
        var integrationStartsCount = 0
        let integration = Integration { _, _ in
            integrationStartsCount += 1
        }

        let integrator = try XCTUnwrap(
            Logger.configure(
                withAPIKey: "foo",
                sessionStrategy: .fixed()
            )
        )

        integrator.enableIntegrations([integration])
        XCTAssertEqual(1, integrationStartsCount)
        integrator.enableIntegrations([integration])
        XCTAssertEqual(1, integrationStartsCount)
    }
}
