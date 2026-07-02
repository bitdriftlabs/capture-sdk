// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
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

    func testOnFirstLaunchReturnsUnknown() throws {
        let controller = try givenController()

        let result = whenResolving(controller, didCrashLastLaunch: false)

        thenResultEquals(result, .unknown)
    }

    func testOnRelaunchWithCrashReturnsFatalCrash() throws {
        try givenController()

        let controller = try givenController()
        let result = whenResolving(controller, didCrashLastLaunch: true)

        thenResultEquals(result, PreviousRunInfo(terminationReason: .fatalCrash))
    }

    func testOnRelaunchWithoutCrashOrCleanExitReturnsUnknown() throws {
        try givenController()

        let controller = try givenController()
        let result = whenResolving(controller, didCrashLastLaunch: false)

        thenResultEquals(result, .unknown)
    }

    func testOnDirectoryUnavailableReturnsNil() throws {
        try givenBaseDirectoryIsBlockedByAFile()

        let controller = whenCreatingController()

        thenControllerIsNil(controller)
    }

    func testOnResolveCalledTwiceKeepsFirstResult() throws {
        try givenController()

        let controller = try givenController()
        whenResolving(controller, didCrashLastLaunch: false)
        let result = whenResolving(controller, didCrashLastLaunch: true)

        // `resolve` is idempotent: only the first call has an effect.
        thenResultEquals(result, .unknown)
    }
}

private extension PreviousRunInfoControllerTests {
    @discardableResult
    func givenController() throws -> PreviousRunInfoController {
        try XCTUnwrap(PreviousRunInfoController(
            baseDirectory: baseDirectoryURL,
            osVersion: osVersion
        ))
    }

    func givenBaseDirectoryIsBlockedByAFile() throws {
        let parent = baseDirectoryURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: parent, withIntermediateDirectories: true)
        // A regular file at the target path makes directory creation fail, exercising the
        // `init?` failure path.
        try Data().write(to: baseDirectoryURL)
    }

    @discardableResult
    func whenResolving(_ controller: PreviousRunInfoController, didCrashLastLaunch: Bool) -> PreviousRunInfo {
        controller.resolve(didCrashLastLaunch: didCrashLastLaunch)
        return controller.previousRunInfo
    }

    func whenCreatingController() -> PreviousRunInfoController? {
        return PreviousRunInfoController(baseDirectory: baseDirectoryURL, osVersion: osVersion)
    }

    func thenResultEquals(_ result: PreviousRunInfo, _ expected: PreviousRunInfo) {
        XCTAssertEqual(result, expected)
    }

    func thenControllerIsNil(_ controller: PreviousRunInfoController?) {
        XCTAssertNil(controller)
    }
}
