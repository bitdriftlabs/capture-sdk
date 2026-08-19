// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import CaptureMocks
import Foundation
import UIKit
import XCTest

@testable import Capture

final class AppStateAttributesTests: XCTestCase {
    func testLifecycleNotificationsUpdateForegroundOotbField() {
        let notificationCenter = NotificationCenterMock()
        let attributes = AppStateAttributes(notificationCenter: notificationCenter)
        let logger = MockCoreLogging()
        attributes.start(with: logger)

        notificationCenter.post(name: UIApplication.didEnterBackgroundNotification, object: nil)
        XCTAssertEqual(logger.ootbFields["foreground"], "0")

        notificationCenter.post(name: UIApplication.willEnterForegroundNotification, object: nil)
        XCTAssertEqual(logger.ootbFields["foreground"], "1")
    }
}
