// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge

final class PreviousRunInfoController {
    private let currentState: PreviousRunCurrentState
    private let previousState: PreviousRunStoredState?
    private let terminationObserver: PreviousRunTerminationObserver
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
            bootTime: self.currentState.bootTime,
            wasDebuggerAttached: self.currentState.wasDebuggerAttached
        )) != nil else {
            return nil
        }

        self.terminationObserver = PreviousRunTerminationObserver(store: store)
        self.terminationObserver.start()
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
