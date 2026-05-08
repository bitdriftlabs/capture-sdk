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

class NotificationCenterSpy: NotificationCenter, @unchecked Sendable {
    var observedNames: [NSNotification.Name] = []
    
    override func addObserver(
        forName name: NSNotification.Name?,
        object: Any?,
        queue: OperationQueue?,
        using block: @escaping (Notification) -> Void
    ) -> NSObjectProtocol {
        if let name {
            observedNames.append(name)
        }
        return super.addObserver(forName: name, object: object, queue: queue, using: block)
    }
    
    var removedObserversCalledCount: Int = 0
    override func removeObserver(_ observer: Any) {
        removedObserversCalledCount += 1
        super.removeObserver(observer)
    }
}

final class LoggerLifecycleControllerTests: XCTestCase {
    func testStartAddsToAllNotifications() {
        let notificationCenter = NotificationCenterSpy()
        let notifications: [NSNotification.Name] = [
            UIApplication.didEnterBackgroundNotification,
            UIApplication.willResignActiveNotification,
            UIApplication.willTerminateNotification
        ]
        
        let sut = LoggerLifecycleController(logger: MockCoreLogging(), notificationCenter: notificationCenter)
        
        sut.start()
        
        notifications.forEach { notification in
            XCTAssertTrue(notificationCenter.observedNames.contains(notification))
        }
    }
    
    func testStopRemovesFromAllNotifications() {
        let notificationCenter = NotificationCenterSpy()
        let sut = LoggerLifecycleController(logger: getLogger(), notificationCenter: notificationCenter)
        
        sut.start()
        sut.stop()
        
        XCTAssertEqual(3, notificationCenter.removedObserversCalledCount)
    }
}

extension LoggerLifecycleControllerTests {
    func getLogger(
        flushOnWillResignActiveEnabledFlag: Bool = true,
        flushOnForceQuitEnabledFlag: Bool = true
    ) -> CoreLogging {
        let logger = MockCoreLogging()
        logger.mockRuntimeVariable(.loggerFlushingOnForceQuit, with: flushOnForceQuitEnabledFlag)
        logger.mockRuntimeVariable(.loggerFlushingOnWillResignActive, with: flushOnWillResignActiveEnabledFlag)
        return logger
    }
}
