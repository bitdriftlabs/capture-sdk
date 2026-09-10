// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge

final class PreviousRunInfoController {
    private let storeDirectory: URL
    private let currentState: PreviousRunCurrentState
    private let previousState: PreviousRunStoredState?
    private var terminationObserver: PreviousRunTerminationObserver?
    private let resolver = PreviousRunResolver()

    private let previousRunInfoStorage = Atomic<PreviousRunInfo?>(nil)
    var previousRunInfo: PreviousRunInfo { self.previousRunInfoStorage.load() ?? .unknown }

    /// This only controls startup replay timing; KSCrash and MetricKit still determine the
    /// previous-run crash result later in `resolve(didCrashLastLaunch:)`.
    var startupReplayEligibility: StartupReplayEligibility {
        guard let previousState else {
            return .unknown
        }

        return previousState.wasCleanExit ? .noPriorCrash : .mayHavePriorCrash
    }

    init(baseDirectory: URL, osVersion: String) {
        self.storeDirectory = baseDirectory.appendingPathComponent("previous_run", isDirectory: true)
        self.currentState = PreviousRunCurrentState.create(osVersion: osVersion)
        self.previousState = BDPreviousRunInfoRepository
            .loadExistingPreviousRunInfo(fromDirectory: self.storeDirectory)
            .map(PreviousRunStoredState.init)
    }

    /// Persists the current-run sentinel and begins observing termination after the startup replay
    /// decision has consumed the read-only previous-run snapshot.
    ///
    /// - returns: Whether the current-run sentinel was prepared and lifecycle observation started.
    @discardableResult
    func startTrackingCurrentRun() -> Bool {
        guard self.terminationObserver == nil else {
            return true
        }

        guard let store = try? BDPreviousRunInfoRepository(directory: self.storeDirectory) else {
            return false
        }

        guard (try? store.prepareCurrentRunInfo(
            withOsVersion: self.currentState.osVersion,
            binaryUUID: self.currentState.binaryUUID,
            bootTime: self.currentState.bootTime,
            wasDebuggerAttached: self.currentState.wasDebuggerAttached
        )) != nil else {
            return false
        }

        let terminationObserver = PreviousRunTerminationObserver(store: store)
        terminationObserver.start()
        self.terminationObserver = terminationObserver
        return true
    }

    /// Resolves the previous-run status. Only the first call has an effect, since the previous/current
    /// launch state this is computed from never changes after `init`. Later calls (e.g. once
    /// crash-reporter initialization determines the final value) are no-ops if a resolution is already
    /// stored.
    ///
    /// - parameter didCrashLastLaunch: Whether the in-process crash reporter captured a fatal crash
    ///                                 during the previous run.
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
