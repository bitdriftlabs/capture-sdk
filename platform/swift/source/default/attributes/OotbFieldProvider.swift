// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CapturePassable

/// Supplies OOTB fields at logger creation and forwards later state changes to the logger.
protocol OotbFieldProvider: AnyObject {
    /// Returns fields that are available before the logger accepts logs.
    func initialOotbFields() -> [Field]

    /// Starts forwarding subsequent field updates.
    func start(with logger: CoreLogging)
}
