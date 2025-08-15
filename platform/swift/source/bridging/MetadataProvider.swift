// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Provides a protocol that the native logger can invoke in order to retrieve metadata that should
/// be included in the log.
@objc
public protocol MetadataProvider {
    /// Returns the current time, relative to UTC epoch.
    ///
    /// - returns: Current time.
    func timestamp() -> TimeInterval

    /// Returns OOTB (out-of-the-box) fields to be included with emitted logs. OOTB fields are fields that
    /// come from the SDK itself.
    ///
    /// - returns: OOTB fields to emit as part of logs.
    func ootbFields() -> [Field]

    /// Returns custom fields to be included with emitted logs. Custom fields are fields provided by SDK
    /// customers.
    ///
    /// - returns: Custom fields to emit as part of logs.
    func customFields() -> [Field]
}
