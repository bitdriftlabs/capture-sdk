// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
@testable import CaptureLoggerBridge
import XCTest

class MemoryPressureLevelTests: XCTestCase {
    func testTransformMemoryPressureEvent() {
        XCTAssertEqual(MemoryPressureLevel.unknown, MemoryPressureLevel.from(DispatchSource.MemoryPressureEvent(rawValue: 123)))
        XCTAssertEqual(MemoryPressureLevel.unknown, MemoryPressureLevel.from(.all))
        XCTAssertEqual(MemoryPressureLevel.normal, MemoryPressureLevel.from(.normal))
        XCTAssertEqual(MemoryPressureLevel.warning, MemoryPressureLevel.from(.warning))
        XCTAssertEqual(MemoryPressureLevel.critical, MemoryPressureLevel.from(.critical))
    }
}
