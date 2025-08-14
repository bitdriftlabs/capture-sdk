// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureMocks
@testable import CaptureTestBridge
import XCTest

final class EventSubscriberTests: XCTestCase {
    func testEventSubscriberDoesNotCrash() {
        let listener = EventSubscriber()
        listener.setUp(
            logger: MockCoreLogging(),
            appStateAttributes: AppStateAttributes(),
            clientAttributes: ClientAttributes(),
            timeProvider: MockTimeProvider()
        )

        run_events_listener_target_test(listener)
    }
}
