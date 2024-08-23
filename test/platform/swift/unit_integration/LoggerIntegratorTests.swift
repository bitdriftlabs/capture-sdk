// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

final class LoggerIntegratorTests: XCTestCase {
    func testIntegrationsAreStartedAtMostOnce() {
        var integrationStartsCount = 0
        let integration = Integration { _, _ in
            integrationStartsCount += 1
        }

        let integrator = LoggerIntegrator(logger: MockLogging())

        integrator.enableIntegrations([integration])
        XCTAssertEqual(1, integrationStartsCount)

        integrator.enableIntegrations([integration])
        XCTAssertEqual(1, integrationStartsCount)
    }

    func testDisableSwizzlingIsPassed() throws {
        var disableSwizzling: Bool?
        let integration = Integration { _, currentDisableSwizzling in
            disableSwizzling = currentDisableSwizzling
        }

        let integrator = LoggerIntegrator(logger: MockLogging())

        integrator.enableIntegrations([integration], disableSwizzling: true)
        XCTAssertTrue(try XCTUnwrap(disableSwizzling))
    }

    func testSwizzlingEnabledByDefault() throws {
        var disableSwizzling: Bool?
        let integration = Integration { _, currentDisableSwizzling in
            disableSwizzling = currentDisableSwizzling
        }

        let integrator = LoggerIntegrator(logger: MockLogging())

        integrator.enableIntegrations([integration])
        XCTAssertFalse(try XCTUnwrap(disableSwizzling))
    }
}
