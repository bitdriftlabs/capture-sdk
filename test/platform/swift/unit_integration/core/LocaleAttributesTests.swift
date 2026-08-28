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

final class LocaleAttributesTests: XCTestCase {
    func testLocale() throws {
        let localeAttributes = LocaleAttributes()
        let localeStr = localeAttributes.initialOotbFields()[0].data as! String
        XCTAssertNotNil(Locale(identifier: localeStr))

        let logger = MockCoreLogging()
        localeAttributes.start(with: logger)

        XCTAssertNil(logger.ootbFields["_locale"])

        NotificationCenter.default.post(name: NSLocale.currentLocaleDidChangeNotification, object: nil)
        let nowLocaleStr = try XCTUnwrap(logger.ootbFields["_locale"])
        XCTAssertNotNil(Locale(identifier: nowLocaleStr))
        XCTAssertEqual(localeStr, nowLocaleStr)
    }
}
