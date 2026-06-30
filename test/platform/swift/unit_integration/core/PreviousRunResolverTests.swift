// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class PreviousRunResolverTests: XCTestCase {
    private let currentState = PreviousRunCurrentState(
        appVersion: "1.2.3",
        osVersion: "18.0",
        binaryUUID: "current-binary",
        bootTime: 123
    )

    func test_returnsUnknown_whenNoPreviousStateExists() {
        let result = PreviousRunResolver().resolve(
            previousState: nil,
            currentState: self.currentState,
            didCrashLastLaunch: false
        )

        XCTAssertEqual(result, PreviousRunInfo.unknown)
    }

    func test_returnsAppUpdate_whenAppVersionChanges() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: "1.2.2",
                osVersion: self.currentState.osVersion,
                binaryUUID: self.currentState.binaryUUID,
                bootTime: self.currentState.bootTime,
                wasCleanExit: false
            ),
            currentState: self.currentState,
            didCrashLastLaunch: false
        )

        XCTAssertEqual(result, PreviousRunInfo(status: .appUpdate))
    }

    func test_returnsAppUpdate_whenBinaryUUIDChanges() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: self.currentState.osVersion,
                binaryUUID: "previous-binary",
                bootTime: self.currentState.bootTime,
                wasCleanExit: false
            ),
            currentState: self.currentState,
            didCrashLastLaunch: false
        )

        XCTAssertEqual(result, PreviousRunInfo(status: .appUpdate))
    }

    func test_returnsOSUpdate_whenOSVersionChanges() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: "17.5",
                binaryUUID: self.currentState.binaryUUID,
                bootTime: self.currentState.bootTime,
                wasCleanExit: false
            ),
            currentState: self.currentState,
            didCrashLastLaunch: false
        )

        XCTAssertEqual(result, PreviousRunInfo(status: .osUpdate))
    }

    func test_returnsUnknown_whenBootTimeChanges() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: self.currentState.osVersion,
                binaryUUID: self.currentState.binaryUUID,
                // A reboot shifts boot time far beyond the sub-second tolerance.
                bootTime: self.currentState.bootTime + 60_000_000,
                wasCleanExit: false
            ),
            currentState: self.currentState,
            didCrashLastLaunch: false
        )

        XCTAssertEqual(result, .unknown)
    }

    func test_returnsCleanExit_whenPreviousRunTerminatedCleanly() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: self.currentState.osVersion,
                binaryUUID: self.currentState.binaryUUID,
                bootTime: self.currentState.bootTime,
                wasCleanExit: true
            ),
            currentState: self.currentState,
            didCrashLastLaunch: false
        )

        XCTAssertEqual(result, PreviousRunInfo(status: .cleanExit))
    }

    func test_returnsFatalCrash_whenCrashReporterCapturedPreviousCrash() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: self.currentState.osVersion,
                binaryUUID: self.currentState.binaryUUID,
                bootTime: self.currentState.bootTime,
                wasCleanExit: false
            ),
            currentState: self.currentState,
            didCrashLastLaunch: true
        )

        XCTAssertEqual(result, PreviousRunInfo(status: .fatalCrash))
    }

    // MARK: - Crash precedence

    func test_returnsFatalCrash_whenAppVersionChangedAndCrashed() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: "1.2.2",
                osVersion: self.currentState.osVersion,
                binaryUUID: self.currentState.binaryUUID,
                bootTime: self.currentState.bootTime,
                wasCleanExit: false
            ),
            currentState: self.currentState,
            didCrashLastLaunch: true
        )

        XCTAssertEqual(result, PreviousRunInfo(status: .fatalCrash))
    }

    func test_returnsFatalCrash_whenOSVersionChangedAndCrashed() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: "17.5",
                binaryUUID: self.currentState.binaryUUID,
                bootTime: self.currentState.bootTime,
                wasCleanExit: false
            ),
            currentState: self.currentState,
            didCrashLastLaunch: true
        )

        XCTAssertEqual(result, PreviousRunInfo(status: .fatalCrash))
    }

    func test_returnsFatalCrash_whenBootTimeChangedAndCrashed() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: self.currentState.osVersion,
                binaryUUID: self.currentState.binaryUUID,
                bootTime: self.currentState.bootTime + 60_000_000,
                wasCleanExit: false
            ),
            currentState: self.currentState,
            didCrashLastLaunch: true
        )

        XCTAssertEqual(result, PreviousRunInfo(status: .fatalCrash))
    }

    func test_returnsFatalCrash_whenCleanExitMarkedAndCrashed() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: self.currentState.osVersion,
                binaryUUID: self.currentState.binaryUUID,
                bootTime: self.currentState.bootTime,
                wasCleanExit: true
            ),
            currentState: self.currentState,
            didCrashLastLaunch: true
        )

        XCTAssertEqual(result, PreviousRunInfo(status: .fatalCrash))
    }

    // MARK: - Clean-exit precedence

    func test_returnsCleanExit_whenBootTimeChangedAndCleanExit() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: self.currentState.osVersion,
                binaryUUID: self.currentState.binaryUUID,
                bootTime: self.currentState.bootTime + 60_000_000,
                wasCleanExit: true
            ),
            currentState: self.currentState,
            didCrashLastLaunch: false
        )

        XCTAssertEqual(result, PreviousRunInfo(status: .cleanExit))
    }

    // MARK: - Boot-time tolerance

    func test_returnsUnknown_whenBootTimeWithinToleranceTreatedAsSameBoot() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: self.currentState.osVersion,
                binaryUUID: self.currentState.binaryUUID,
                // Sub-second drift is not a reboot; falls through to `.unknown`.
                bootTime: self.currentState.bootTime + 500,
                wasCleanExit: false
            ),
            currentState: self.currentState,
            didCrashLastLaunch: false
        )

        XCTAssertEqual(result, .unknown)
    }

    func test_returnsUnknown_whenBootTimeIsZero() {
        let result = PreviousRunResolver().resolve(
            previousState: .init(
                appVersion: self.currentState.appVersion,
                osVersion: self.currentState.osVersion,
                binaryUUID: self.currentState.binaryUUID,
                bootTime: 0,
                wasCleanExit: false
            ),
            currentState: self.currentState,
            didCrashLastLaunch: false
        )

        XCTAssertEqual(result, .unknown)
    }
}
