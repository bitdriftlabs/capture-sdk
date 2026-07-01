// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge

struct PreviousRunStoredState: Equatable {
    let appVersion: String
    let osVersion: String
    let binaryUUID: String
    let bootTime: UInt64
    let wasCleanExit: Bool
    let wasDebuggerAttached: Bool

    init(
        appVersion: String,
        osVersion: String,
        binaryUUID: String,
        bootTime: UInt64,
        wasCleanExit: Bool,
        wasDebuggerAttached: Bool = false
    ) {
        self.appVersion = appVersion
        self.osVersion = osVersion
        self.binaryUUID = binaryUUID
        self.bootTime = bootTime
        self.wasCleanExit = wasCleanExit
        self.wasDebuggerAttached = wasDebuggerAttached
    }

    init(_ snapshot: BDPreviousRunInfoSnapshot) {
        self.appVersion = snapshot.appVersion
        self.osVersion = snapshot.osVersion
        self.binaryUUID = snapshot.binaryUUID
        self.bootTime = snapshot.bootTime
        self.wasCleanExit = snapshot.wasCleanExit
        self.wasDebuggerAttached = snapshot.wasDebuggerAttached
    }
}
