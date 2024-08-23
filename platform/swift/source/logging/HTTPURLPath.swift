// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// The URL path that consists of the path itself and a template that's supposed to be a path with high
/// cardinality portions of it (if any) replaced with a placeholders such as "<id>".
public struct HTTPURLPath {
    let value: String
    let template: String?

    /// Initializes a new instance of the receiver.
    ///
    /// - parameter value:    The value of the path (i.e., "/path/12345").
    /// - parameter template: The path template (i.e., "/path/<id>"). If the template is not specified,
    ///                       the SDK detects and replaces high-cardinality path portions with the "<id>"
    ///                       placeholder.
    public init(value: String, template: String? = nil) {
        self.value = value
        self.template = template
    }
}
