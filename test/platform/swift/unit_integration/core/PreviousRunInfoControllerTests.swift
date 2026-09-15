// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureLoggerBridge
import Foundation
import XCTest

final class PreviousRunInfoControllerTests: XCTestCase {
    private var baseDirectoryURL: URL!

    private let osVersion = "18.0"

    override func setUp() {
        baseDirectoryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        Debugger.mockIsAttached(false)
    }

    override func tearDown() {
        Debugger.unmock()
        try? FileManager.default.removeItem(at: baseDirectoryURL)
    }

    func testOnFirstLaunchReturnsUnknown() {
        let controller = givenController()

        let result = whenResolving(controller, didCrashLastLaunch: false)

        thenResultEquals(result, .unknown)
    }

    func testOnRelaunchWithCrashReturnsFatalCrash() {
        _ = givenController()

        let controller = givenController()
        let result = whenResolving(controller, didCrashLastLaunch: true)

        thenResultEquals(result, PreviousRunInfo(terminationReason: .fatalCrash))
    }

    func testOnRelaunchWithoutCrashOrCleanExitReturnsUnknown() {
        _ = givenController()

        let controller = givenController()
        let result = whenResolving(controller, didCrashLastLaunch: false)

        thenResultEquals(result, .unknown)
    }

    func testDoesNotInitializeWhenDirectoryIsUnavailable() throws {
        try givenBaseDirectoryIsBlockedByAFile()

        XCTAssertNil(PreviousRunInfoController(baseDirectory: baseDirectoryURL, osVersion: osVersion))
    }

    func testOnResolveCalledTwiceKeepsFirstResult() {
        _ = givenController()

        let controller = givenController()
        whenResolving(controller, didCrashLastLaunch: false)
        let result = whenResolving(controller, didCrashLastLaunch: true)

        // `resolve` is idempotent: only the first call has an effect.
        thenResultEquals(result, .unknown)
    }

    func testStartupReplayEligibilityUsesCleanExitMarker() throws {
        try givenPersistedPreviousRunInfo(wasCleanExit: true)
        let controller = givenController()

        XCTAssertEqual(
            controller.startupReplayEligibility,
            .noPriorCrash
        )
    }

    func testStartupReplayEligibilityUsesUncleanExitMarker() throws {
        try givenPersistedPreviousRunInfo(wasCleanExit: false)
        let controller = givenController()

        XCTAssertEqual(
            controller.startupReplayEligibility,
            .mayHavePriorCrash
        )
    }

    func testCreatesSentinelWhenInitialized() {
        XCTAssertFalse(FileManager.default.fileExists(atPath: previousRunDirectoryURL.path))
        _ = givenController()

        XCTAssertTrue(FileManager.default.fileExists(atPath: previousRunDirectoryURL.path))
    }

    func testStartupReplayEligibilityRawValuesMatchSharedCore() {
        XCTAssertEqual(StartupReplayEligibility.noPriorCrash.rawValue, 0)
        XCTAssertEqual(StartupReplayEligibility.mayHavePriorCrash.rawValue, 1)
        XCTAssertEqual(StartupReplayEligibility.unknown.rawValue, 2)
    }
}

private extension PreviousRunInfoControllerTests {
    var previousRunDirectoryURL: URL {
        baseDirectoryURL.appendingPathComponent("previous_run", isDirectory: true)
    }

    func givenController() -> PreviousRunInfoController {
        guard let controller = PreviousRunInfoController(
            baseDirectory: baseDirectoryURL,
            osVersion: osVersion
        ) else {
            XCTFail("PreviousRunInfoController should initialize")
            fatalError("PreviousRunInfoController initialization failed")
        }
        return controller
    }

    func givenBaseDirectoryIsBlockedByAFile() throws {
        let parent = baseDirectoryURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: parent, withIntermediateDirectories: true)
        // A regular file at the target path makes directory creation fail.
        try Data().write(to: baseDirectoryURL)
    }

    func givenPersistedPreviousRunInfo(wasCleanExit: Bool) throws {
        let store = try BDPreviousRunInfoRepository(directory: previousRunDirectoryURL)
        try store.prepareCurrentRunInfo(
            withOsVersion: osVersion,
            binaryUUID: "4f179445-15d8-4ec1-a86f-0dfe9d2bb425",
            bootTime: 123_456_789,
            wasDebuggerAttached: false
        )
        if wasCleanExit {
            store.markTerminating()
        }
    }

    @discardableResult
    func whenResolving(_ controller: PreviousRunInfoController, didCrashLastLaunch: Bool) -> PreviousRunInfo {
        controller.resolve(didCrashLastLaunch: didCrashLastLaunch)
        return controller.previousRunInfo
    }

    func thenResultEquals(_ result: PreviousRunInfo, _ expected: PreviousRunInfo) {
        XCTAssertEqual(result, expected)
    }
}
