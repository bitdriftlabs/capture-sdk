// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class WebViewScriptConfigurationTests: XCTestCase {
    func testDefaultConfigurationEnablesAllCaptures() throws {
        let sut = WebViewScriptConfiguration()
        let allEnabledJSON = """
        {
            "capturePageViews": true,
            "captureNetworkRequests": true,
            "captureNavigationEvents": true,
            "captureWebVitals": true,
            "captureLongTasks": true,
            "captureConsoleLogs": true,
            "captureUserInteractions": true,
            "captureErrors": true
        }
        """
        let decoded = try decodeConfiguration(from: allEnabledJSON)
        XCTAssertEqual(sut, decoded)
    }

    func testCustomConfigurationOnlyDisablesSpecifiedCaptures() throws {
        let sut = WebViewScriptConfiguration(captureWebVitals: false, captureUserInteractions: false)
        let json = """
        {
            "capturePageViews": true,
            "captureNetworkRequests": true,
            "captureNavigationEvents": true,
            "captureWebVitals": false,
            "captureLongTasks": true,
            "captureConsoleLogs": true,
            "captureUserInteractions": false,
            "captureErrors": true
        }
        """
        let decoded = try decodeConfiguration(from: json)
        XCTAssertEqual(sut, decoded)
    }

    func testToJSONStringRoundTripsToAnEqualConfiguration() throws {
        let sut = WebViewScriptConfiguration(
            capturePageViews: false,
            captureNetworkRequests: true,
            captureNavigationEvents: false,
            captureWebVitals: true,
            captureLongTasks: false,
            captureConsoleLogs: true,
            captureUserInteractions: false,
            captureErrors: true
        )

        let decoded = try decodeConfiguration(from: sut.toJSONString())

        XCTAssertEqual(sut, decoded)
    }
}

private extension WebViewScriptConfigurationTests {
    func decodeConfiguration(from json: String) throws -> WebViewScriptConfiguration {
        try JSONDecoder().decode(WebViewScriptConfiguration.self, from: Data(json.utf8))
    }
}
