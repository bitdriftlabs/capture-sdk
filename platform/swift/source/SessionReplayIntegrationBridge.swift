// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

@_cdecl("capture_log_session_replay_screen_for_active_logger")
func capture_log_session_replay_screen_for_active_logger(_ data: NSData, _ duration: Double) {
    guard let logger = Logger.getShared() as? Logger else {
        return
    }

    logger.logSessionReplayScreen(
        screen: SessionReplayCapture(data: data as Data),
        duration: duration
    )
}
