// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

/// Responsible for emitting session replay logs in response to provided callbacks.
@objc
public protocol SessionReplayTarget {
    /// Called to indicate that the target is supposed to prepare and emit a session replay screen log.
    func captureScreen()
}
