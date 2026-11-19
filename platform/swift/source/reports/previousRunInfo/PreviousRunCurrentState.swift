// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge

struct PreviousRunCurrentState: Equatable {
    let osVersion: String
    let binaryUUID: String
    let bootTime: UInt64
    let wasDebuggerAttached: Bool

    static func create(osVersion: String) -> PreviousRunCurrentState {
        let build = BDPreviousRunStateCaptureSupport.osBuildVersion() ?? ""
        let fullOsVersion = build.isEmpty ? osVersion : "\(osVersion) (\(build))"
        return PreviousRunCurrentState(
            osVersion: fullOsVersion,
            binaryUUID: BDPreviousRunStateCaptureSupport.mainBinaryUUID() ?? "",
            bootTime: BDPreviousRunStateCaptureSupport.systemBootTime(),
            wasDebuggerAttached: Debugger.isAttached()
        )
    }
}
