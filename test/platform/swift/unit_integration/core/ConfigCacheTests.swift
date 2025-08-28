// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import Foundation
import XCTest

final class ConfigCacheTests: XCTestCase {
    func testReadValues() {
        let input = "key,true\nother,false\nmore.stuff,a bit of cheese"
        let values = readCachedValues(input)
        XCTAssert(values != nil)
        XCTAssertEqual(true, values!["key"] as! Bool)
        XCTAssertEqual(false, values!["other"] as! Bool)
        XCTAssertEqual("a bit of cheese", values!["more.stuff"] as! String)
    }
}
