// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import CaptureMocks
import Foundation
import XCTest

@testable import Capture

final class LoggerLifecycleControllerTests: XCTestCase {
    func testStartAddsToAllNotifications() {
        let notificationCenter = NotificationCenterMock()
        let notifications: [NSNotification.Name] = [
            UIApplication.didEnterBackgroundNotification,
            UIApplication.willResignActiveNotification,
            UIApplication.willTerminateNotification,
        ]

        let sut = LoggerLifecycleController(
            logger: makeLogger(), notificationCenter: notificationCenter)

        sut.start()

        notifications.forEach { notification in
            XCTAssertTrue(notificationCenter.observedNames.contains(notification))
        }
    }

    func testStopRemovesFromAllNotifications() {
        let notificationCenter = NotificationCenterMock()
        let sut = LoggerLifecycleController(
            logger: makeLogger(), notificationCenter: notificationCenter)

        sut.start()
        sut.stop()

        XCTAssertEqual(3, notificationCenter.removedObserversCalledCount)
    }

    // MARK: - WillTerminate tests

    func testOnReceivingWillTerminateNotificationFlushesBlockingWhenForceQuitEnabled() {
        let notificationCenter = NotificationCenterMock()
        let logger = makeLogger(
            flushOnWillResignActiveEnabledFlag: false,
            flushOnForceQuitEnabledFlag: true
        )
        let sut = LoggerLifecycleController(logger: logger, notificationCenter: notificationCenter)
        sut.start()

        notificationCenter.post(name: UIApplication.willTerminateNotification, object: nil)

        XCTAssertEqual([true], logger.flushCalls)
    }

    func testOnReceivingWillTerminateNotificationDoesNotFlushWhenForceQuitDisabled() {
        let notificationCenter = NotificationCenterMock()
        let logger = makeLogger(
            flushOnWillResignActiveEnabledFlag: false,
            flushOnForceQuitEnabledFlag: false
        )
        let sut = LoggerLifecycleController(logger: logger, notificationCenter: notificationCenter)
        sut.start()

        notificationCenter.post(name: UIApplication.willTerminateNotification, object: nil)

        XCTAssertTrue(logger.flushCalls.isEmpty)
    }

    func testOnReceivingWillTerminateNotificationDoesNotFlushWhenWillResignActiveFlushIsEnabled() {
        // If we already flush on willResignActive, willTerminate must not double-flush.
        let notificationCenter = NotificationCenterMock()
        let logger = makeLogger(
            flushOnWillResignActiveEnabledFlag: true,
            flushOnForceQuitEnabledFlag: true
        )
        let sut = LoggerLifecycleController(logger: logger, notificationCenter: notificationCenter)
        sut.start()

        notificationCenter.post(name: UIApplication.willTerminateNotification, object: nil)

        XCTAssertTrue(logger.flushCalls.isEmpty)
    }

    // MARK: - willResignActive tests

    func testOnReceivingWillResignACtiveNotificationFlushesNonBlockingWhenFlagEnabled() {
        let notificationCenter = NotificationCenterMock()
        let logger = makeLogger(
            flushOnWillResignActiveEnabledFlag: true,
            )
        let sut = LoggerLifecycleController(logger: logger, notificationCenter: notificationCenter)
        sut.start()

        notificationCenter.post(name: UIApplication.willResignActiveNotification, object: nil)

        XCTAssertEqual([false], logger.flushCalls)
    }

    func testOnReceivingWillResignACtiveNotificationDoesNotFlushWhenFlagDisabled() {
        let notificationCenter = NotificationCenterMock()
        let logger = makeLogger(
            flushOnWillResignActiveEnabledFlag: false,
            )
        let sut = LoggerLifecycleController(logger: logger, notificationCenter: notificationCenter)
        sut.start()

        notificationCenter.post(name: UIApplication.willResignActiveNotification, object: nil)

        XCTAssertTrue(logger.flushCalls.isEmpty)
    }

    // MARK: - didEnterBackground tests

    func testOnReceivingDidEnterBackgroundFlushesNonBlockingWhenWillResignFlagDisabled() {
        let notificationCenter = NotificationCenterMock()
        let logger = makeLogger(
            flushOnWillResignActiveEnabledFlag: false,
            )
        let sut = LoggerLifecycleController(logger: logger, notificationCenter: notificationCenter)
        sut.start()

        notificationCenter.post(name: UIApplication.didEnterBackgroundNotification, object: nil)

        XCTAssertEqual([false], logger.flushCalls)
    }

    func testOnReceivingDidEnterBackgroundDoesNotFlusheWhenWillResignFlagEnabled() {
        let notificationCenter = NotificationCenterMock()
        let logger = makeLogger(
            flushOnWillResignActiveEnabledFlag: true,
            )
        let sut = LoggerLifecycleController(logger: logger, notificationCenter: notificationCenter)
        sut.start()

        notificationCenter.post(name: UIApplication.didEnterBackgroundNotification, object: nil)

        XCTAssertTrue(logger.flushCalls.isEmpty)
    }
}

extension LoggerLifecycleControllerTests {
    func makeLogger(
        flushOnWillResignActiveEnabledFlag: Bool = true,
        flushOnForceQuitEnabledFlag: Bool = true
    ) -> MockCoreLogging {
        let logger = MockCoreLogging()
        logger.mockRuntimeVariable(.loggerFlushingOnForceQuit, with: flushOnForceQuitEnabledFlag)
        logger.mockRuntimeVariable(
            .loggerFlushingOnWillResignActive, with: flushOnWillResignActiveEnabledFlag)
        return logger
    }
}
