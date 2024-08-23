// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// This interface defines the accessors to provide custom fields that will be sent alongside all log lines.
public protocol FieldProvider {
    /// Returns the set of fields that will be included on every log line. Keep in mind this function could be
    /// called multiple times per second so it’s recommended to access expensive fields sporadically.
    /// Try to make this function as cheap as possible.
    ///
    /// - returns: Fields to capture.
    func getFields() -> Fields
}
