// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

/// Provides custom fields for emitted logs.
@objc
public protocol CustomFieldsProvider {
    /// Returns custom fields provided by SDK customers.
    ///
    /// - returns: Custom fields to emit with the log.
    func customFields() -> [Field]
}
