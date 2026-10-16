// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import XCTest

final class E2EStartupConfigurationTests: XCTestCase {
    private let environment = [
        "BITDRIFT_E2E": "1",
        "BITDRIFT_E2E_API_URL": "http://127.0.0.1:8001",
        "BITDRIFT_E2E_API_KEY": "test-only-key",
        "BITDRIFT_E2E_RUN": "unique-test-run",
    ]

    func testOrdinaryLaunchDoesNotUseOverrides() {
        XCTAssertEqual(E2EStartupConfiguration.resolve(environment: [:]), .normal)
        XCTAssertEqual(E2EStartupConfiguration.resolve(environment: ["UNRELATED": "1"]), .normal)
    }

    func testValidConfiguration() throws {
        let url = try XCTUnwrap(URL(string: "http://127.0.0.1:8001"))
        XCTAssertEqual(
            E2EStartupConfiguration.resolve(environment: self.environment),
            .enabled(apiURL: url, apiKey: "test-only-key", run: "unique-test-run")
        )
    }

    func testPartialConfigurationCannotFallBack() {
        for key in self.environment.keys {
            var missing = self.environment
            missing.removeValue(forKey: key)
            XCTAssertEqual(E2EStartupConfiguration.resolve(environment: missing), .invalid, key)
            XCTAssertEqual(
                E2EStartupConfiguration.resolve(environment: [key: self.environment[key] ?? ""]),
                .invalid,
                key
            )
        }
    }

    func testInvalidFlagsAndEmptyValuesCannotFallBack() {
        for key in self.environment.keys {
            for value in ["", " ", "\n"] {
                var invalid = self.environment
                invalid[key] = value
                XCTAssertEqual(E2EStartupConfiguration.resolve(environment: invalid), .invalid, key)
            }
        }
        for flag in ["0", "true", "2"] {
            var invalid = self.environment
            invalid["BITDRIFT_E2E"] = flag
            XCTAssertEqual(E2EStartupConfiguration.resolve(environment: invalid), .invalid)
        }
    }

    func testOnlyLoopbackHTTPOriginsAreAccepted() {
        for rawURL in [
            "https://api.bitdrift.io", "http://127.0.0.1.example.com:8001",
            "http://192.168.1.1:8001", "file:///localhost", "ftp://localhost",
            "http://secret@localhost:8001", "http://localhost:8001/api",
            "http://localhost:8001?key=secret", "http://localhost:8001#fragment",
            "http://localhost:0", "http://localhost:65536", " http://localhost:8001",
        ] {
            var invalid = self.environment
            invalid["BITDRIFT_E2E_API_URL"] = rawURL
            XCTAssertEqual(E2EStartupConfiguration.resolve(environment: invalid), .invalid, rawURL)
        }
        for rawURL in ["http://localhost:8001/", "http://[::1]:8001", "https://127.0.0.1:8001"] {
            var valid = self.environment
            valid["BITDRIFT_E2E_API_URL"] = rawURL
            guard case .enabled = E2EStartupConfiguration.resolve(environment: valid) else {
                XCTFail("Rejected loopback origin: \(rawURL)")
                continue
            }
        }
    }

    func testResolutionDoesNotPersistCredentials() {
        let defaultsBefore = UserDefaults.standard.dictionaryRepresentation() as NSDictionary
        _ = E2EStartupConfiguration.resolve(environment: self.environment)
        XCTAssertEqual(defaultsBefore, UserDefaults.standard.dictionaryRepresentation() as NSDictionary)
    }
}
