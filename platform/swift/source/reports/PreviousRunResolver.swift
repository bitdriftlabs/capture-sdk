// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation
import UIKit

struct PreviousRunStoredState: Equatable {
    let appVersion: String
    let osVersion: String
    let binaryUUID: String
    let bootTime: UInt64
    let wasCleanExit: Bool

    init(
        appVersion: String,
        osVersion: String,
        binaryUUID: String,
        bootTime: UInt64,
        wasCleanExit: Bool
    ) {
        self.appVersion = appVersion
        self.osVersion = osVersion
        self.binaryUUID = binaryUUID
        self.bootTime = bootTime
        self.wasCleanExit = wasCleanExit
    }

    init(_ snapshot: BDPreviousRunInfoSnapshot) {
        self.appVersion = snapshot.appVersion
        self.osVersion = snapshot.osVersion
        self.binaryUUID = snapshot.binaryUUID
        self.bootTime = snapshot.bootTime
        self.wasCleanExit = snapshot.wasCleanExit
    }
}

struct PreviousRunCurrentState: Equatable {
    let appVersion: String
    let osVersion: String
    let binaryUUID: String
    let bootTime: UInt64

    static func create(
        appVersion: String,
        osVersion: String
    ) -> PreviousRunCurrentState {
        return PreviousRunCurrentState(
            appVersion: appVersion,
            osVersion: osVersion,
            binaryUUID: BDPreviousRunStateCaptureSupport.mainBinaryUUID() ?? "",
            bootTime: BDPreviousRunStateCaptureSupport.systemBootTime()
        )
    }
}

struct PreviousRunResolver {
    /// Boot time is captured in microseconds. Two launches within the same boot session can read
    /// values that differ slightly because the kernel derives boot time as `now - uptime` and may
    /// adjust it (e.g. NTP). A tolerance avoids misreading those sub-second adjustments as a reboot.
    private static let bootTimeToleranceMicroseconds: UInt64 = 1_000_000

    func resolve(
        previousState: PreviousRunStoredState?,
        currentState: PreviousRunCurrentState,
        didCrashLastLaunch: Bool
    ) -> PreviousRunInfo
    {
        guard let previousState else {
            return .unknown
        }

        // A crash captured by the in-process reporter is the most authoritative signal: it remains
        // valid regardless of app/OS updates or reboots, so it takes precedence over everything else.
        if didCrashLastLaunch {
            return PreviousRunInfo(status: .fatalCrash)
        }

        // A clean-exit marker means the previous run terminated gracefully (received willTerminate).
        if previousState.wasCleanExit {
            return PreviousRunInfo(status: .cleanExit)
        }

        // The previous run did not exit cleanly and no crash was captured. Try to explain why with
        // launch-time signals. Note: an OS update always implies a reboot, so the OS-update check
        // must run before the boot-time check below, otherwise OS updates would be reported as
        // `.unknown`.
        if previousState.appVersion != currentState.appVersion ||
            previousState.binaryUUID != currentState.binaryUUID {
            return PreviousRunInfo(status: .appUpdate)
        }

        if previousState.osVersion != currentState.osVersion {
            return PreviousRunInfo(status: .osUpdate)
        }

        if previousState.bootTime != 0,
           currentState.bootTime != 0,
           Self.bootTimeDelta(previousState.bootTime, currentState.bootTime) > Self.bootTimeToleranceMicroseconds {
            // The device rebooted between runs while the previous run ended without a clean-exit
            // marker or captured crash. This currently collapses to `.unknown`, same as the
            // fallthrough below.
            // TODO: introduce a dedicated status (e.g. forced reboot / power-off) if we want to
            // distinguish a reboot-induced termination from a genuinely unknown one.
            return .unknown
        }

        return .unknown
    }

    private static func bootTimeDelta(_ lhs: UInt64, _ rhs: UInt64) -> UInt64 {
        return lhs > rhs ? lhs - rhs : rhs - lhs
    }
}

final class PreviousRunInfoController {
    private let currentState: PreviousRunCurrentState
    private let previousState: PreviousRunStoredState?
    private let stateCapture: PreviousRunStateCapture
    private let resolver = PreviousRunResolver()

    private let previousRunInfoStorage = Atomic<PreviousRunInfo?>(nil)
    var previousRunInfo: PreviousRunInfo { self.previousRunInfoStorage.load() ?? .unknown }

    init?(baseDirectory: URL, appVersion: String, osVersion: String) {
        let storeDirectory = baseDirectory.appendingPathComponent("reports/previous_run", isDirectory: true)
        guard let store = try? BDPreviousRunInfoRepository(directory: storeDirectory) else {
            return nil
        }

        self.currentState = PreviousRunCurrentState.create(
            appVersion: appVersion,
            osVersion: osVersion
        )
        self.previousState = (try? store.loadPreviousRunInfo()).map(PreviousRunStoredState.init)

        guard (try? store.prepareCurrentRunInfo(
            withAppVersion: self.currentState.appVersion,
            osVersion: self.currentState.osVersion,
            binaryUUID: self.currentState.binaryUUID,
            bootTime: self.currentState.bootTime
        )) != nil else {
            return nil
        }

        self.stateCapture = PreviousRunStateCapture(store: store)
        self.stateCapture.start()
    }

    /// Resolves the previous-run status using `didCrashLastLaunch`. It's only the first call has
    /// an effect, since the previous/current launch state this is computed from never changes after
    /// `init`. Later calls (e.g. once crash-reporter initialization determines the final value) are
    /// no-ops if a resolution is already stored.
    func resolve(didCrashLastLaunch: Bool) {
        self.previousRunInfoStorage.update { stored in
            guard stored == nil else {
                return
            }

            stored = self.resolver.resolve(
                previousState: self.previousState,
                currentState: self.currentState,
                didCrashLastLaunch: didCrashLastLaunch
            )
        }
    }
}

final class PreviousRunStateCapture {
    private let store: BDPreviousRunInfoRepository
    private let notificationCenter: NotificationCenter
    private var token: NSObjectProtocol?

    init(
        store: BDPreviousRunInfoRepository,
        notificationCenter: NotificationCenter = .default
    ) {
        self.store = store
        self.notificationCenter = notificationCenter
    }

    deinit {
        if let token {
            self.notificationCenter.removeObserver(token)
        }
    }

    func start() {
        guard self.token == nil else {
            return
        }

        self.token = self.notificationCenter.addObserver(
            forName: UIApplication.willTerminateNotification,
            object: nil,
            queue: nil
        ) { [weak self] _ in
            self?.store.markTerminating()
        }
    }
}
