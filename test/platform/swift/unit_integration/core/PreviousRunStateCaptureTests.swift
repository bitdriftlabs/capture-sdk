// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureLoggerBridge
import Foundation
import UIKit
import XCTest

final class PreviousRunStateCaptureTests: XCTestCase {
    private var directoryURL: URL!
    private var notificationCenter: NotificationCenter!
    private var store: BDPreviousRunInfoRepository!
    private var stateCapture: PreviousRunStateCapture?

    private let appVersion = "1.2.3"
    private let osVersion = "18.0"
    private let binaryUUID = "4f179445-15d8-4ec1-a86f-0dfe9d2bb425"
    private let bootTime: UInt64 = 123_456_789

    override func setUpWithError() throws {
        directoryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        notificationCenter = NotificationCenter()
        store = try BDPreviousRunInfoRepository(directory: directoryURL)
        try store.prepareCurrentRunInfo(
            withAppVersion: appVersion,
            osVersion: osVersion,
            binaryUUID: binaryUUID,
            bootTime: bootTime
        )
    }

    override func tearDown() {
        stateCapture = nil
        store = nil
        try? FileManager.default.removeItem(at: directoryURL)
    }

    func testOnWillTerminateNotificationMarksCleanExitForNextLaunch() throws {
        givenStateCaptureStarted()

        whenWillTerminateNotificationIsPosted()

        try thenNextLaunchSeesCleanExit(true)
    }

    func testWithoutNotificationNextLaunchSeesUncleanExit() throws {
        givenStateCaptureStarted()

        try thenNextLaunchSeesCleanExit(false)
    }

    func testOnDeinitRemovesObserverAndStopsMarkingCleanExit() throws {
        givenStateCaptureStarted()

        whenStateCaptureIsDeallocated()
        whenWillTerminateNotificationIsPosted()

        try thenNextLaunchSeesCleanExit(false)
    }
}

private extension PreviousRunStateCaptureTests {
    func givenStateCaptureStarted() {
        stateCapture = PreviousRunStateCapture(store: store, notificationCenter: notificationCenter)
        stateCapture?.start()
    }

    func whenWillTerminateNotificationIsPosted() {
        notificationCenter.post(name: UIApplication.willTerminateNotification, object: nil)
    }

    func whenStateCaptureIsDeallocated() {
        stateCapture = nil
    }

    func thenNextLaunchSeesCleanExit(_ expected: Bool) throws {
        let freshStore = try BDPreviousRunInfoRepository(directory: directoryURL)
        let snapshot = try XCTUnwrap(try freshStore.loadPreviousRunInfo())
        XCTAssertEqual(snapshot.wasCleanExit, expected)
    }
}
