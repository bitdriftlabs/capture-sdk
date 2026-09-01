// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Provides timestamps for emitted logs.
@objc
public protocol TimestampProvider {
    /// Returns the current time, relative to UTC epoch.
    ///
    /// - returns: Current time.
    func timestamp() -> TimeInterval
}

/// Provides custom fields for emitted logs.
@objc
public protocol CustomFieldsProvider {
    /// Returns custom fields provided by SDK customers.
    ///
    /// - returns: Custom fields to emit with the log.
    func customFields() -> [Field]
}
