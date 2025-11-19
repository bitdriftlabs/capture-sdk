// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

final class SessionURLTests: XCTestCase {
    override func tearDown() {
        super.tearDown()
        Logger.resetShared()
    }

    func testDefaultSessionUrl() throws {
        Logger.start(
            withAPIKey: "api_key",
            sessionStrategy: .fixed(),
            configuration: .init()
        )
        let sessionID = try XCTUnwrap(Logger.sessionID)
        XCTAssertEqual(Logger.sessionURL, "https://timeline.bitdrift.io/s/\(sessionID)?utm_source=sdk")
    }

    func testCustomSessionUrlWithQuery() throws {
        try self.configureLogger(apiURL: "https://api.foobar.bitdrift.io?utm_foobar=1")
        let sessionID = try XCTUnwrap(Logger.sessionID)
        XCTAssertEqual(
            Logger.sessionURL,
            "https://timeline.foobar.bitdrift.io/s/\(sessionID)?utm_source=sdk"
        )
    }

    func testCustomSessionUrlWithPath() throws {
        try self.configureLogger(apiURL: "https://api.api.mycompany.bitdrift.io/v1/path")
        let sessionID = try XCTUnwrap(Logger.sessionID)
        XCTAssertEqual(
            Logger.sessionURL,
            "https://timeline.api.mycompany.bitdrift.io/s/\(sessionID)?utm_source=sdk"
        )
    }

    func testCustomSessionUrl1() throws {
        try self.configureLogger(apiURL: "https://api.api.mycompany.bitdrift.io")
        let sessionID = try XCTUnwrap(Logger.sessionID)
        XCTAssertEqual(
            Logger.sessionURL,
            "https://timeline.api.mycompany.bitdrift.io/s/\(sessionID)?utm_source=sdk"
        )
    }

    func testCustomSessionUrl2() throws {
        try self.configureLogger(apiURL: "https://api.myapicompany.bitdrift.io")
        let sessionID = try XCTUnwrap(Logger.sessionID)
        XCTAssertEqual(
            Logger.sessionURL,
            "https://timeline.myapicompany.bitdrift.io/s/\(sessionID)?utm_source=sdk"
        )
    }

    func testCustomSessionUrl3() throws {
        try self.configureLogger(apiURL: "https://api.companyapi.bitdrift.io")
        let sessionID = try XCTUnwrap(Logger.sessionID)
        XCTAssertEqual(
            Logger.sessionURL,
            "https://timeline.companyapi.bitdrift.io/s/\(sessionID)?utm_source=sdk"
        )
    }

    func testSimpleApiUrl() throws {
        try self.configureLogger(apiURL: "https://mycustomapiurl.com")
        let sessionID = try XCTUnwrap(Logger.sessionID)
        XCTAssertEqual(Logger.sessionURL, "https://mycustomapiurl.com/s/\(sessionID)?utm_source=sdk")
    }

    private func configureLogger(apiURL: String) throws {
        Logger.start(
            withAPIKey: "api_key",
            sessionStrategy: .fixed(),
            configuration: .init(apiURL: try XCTUnwrap(URL(string: apiURL))),
        )
    }
}
