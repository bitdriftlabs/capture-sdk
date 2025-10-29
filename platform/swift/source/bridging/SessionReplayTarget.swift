// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Responsible for emitting session replay logs in response to provided callbacks.
@objc
public protocol SessionReplayTarget {
    /// Called to indicate that the target is supposed to prepare and emit a session replay screen log.
    func captureScreen()
    // Called to indicate that the target should prepare and emit a session replay screenshot log.
    // The Rust logger does not request another screenshot until it receives the previously
    // requested one. This mechanism is designed to ensure that there are no situations where
    // the Rust logger requests screenshots at a rate faster than the platform layer can handle.
    func captureScreenshot()
}
