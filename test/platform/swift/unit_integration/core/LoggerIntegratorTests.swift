// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc.
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
import Foundation
import XCTest

final class LoggerIntegratorTests: XCTestCase {
    func testIntegrationsAreStartedAtMostOnce() {
        var integrationStartsCount = 0
        let integration = Integration { _, _, _ in
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
        let integration = Integration { _, currentDisableSwizzling, _ in
            disableSwizzling = currentDisableSwizzling
        }

        let integrator = LoggerIntegrator(logger: MockLogging())

        integrator.enableIntegrations([integration], disableSwizzling: true)
        XCTAssertTrue(try XCTUnwrap(disableSwizzling))
    }

    func testSwizzlingEnabledByDefault() throws {
        var disableSwizzling: Bool?
        let integration = Integration { _, currentDisableSwizzling, _ in
            disableSwizzling = currentDisableSwizzling
        }

        let integrator = LoggerIntegrator(logger: MockLogging())

        integrator.enableIntegrations([integration])
        XCTAssertFalse(try XCTUnwrap(disableSwizzling))
    }

    func testCustomRequestFieldProviderIsPassed() throws {
        var receivedFields: [String: String]?
        let fakeProvider = FakeURLSessionRequestFieldProvider()
        let integration = Integration { _, _, provider in
            let request = URLRequest(url: URL(string: "https://example.com")!)
            receivedFields = provider.provideExtraFields(for: request)
        }

        let integrator = LoggerIntegrator(logger: MockLogging())
        integrator.enableIntegrations([integration], requestFieldProvider: fakeProvider)

        XCTAssertEqual(receivedFields?["mock_field"], "mock_value")
    }

    func testDefaultRequestFieldProviderShouldReturnEmptyExtraFields() throws {
        var receivedFields: [String: String]?
        let integration = Integration { _, _, provider in
            let request = URLRequest(url: URL(string: "https://example.com")!)
            receivedFields = provider.provideExtraFields(for: request)
        }

        let integrator = LoggerIntegrator(logger: MockLogging())
        integrator.enableIntegrations([integration])

        XCTAssertTrue(receivedFields?.isEmpty ?? false)
    }
}

// MARK: - Fake URLSessionRequestFieldProvider

private class FakeURLSessionRequestFieldProvider: URLSessionRequestFieldProvider {
    func provideExtraFields(for request: URLRequest) -> [String: String] {
        return ["mock_field": "mock_value"]
    }
}
