// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

extension URL {
    /// Initializes a new instance of the receiver using provided static string. The caller
    /// is responsible for ensuring that provided string is a valid URL.
    ///
    /// - parameter staticString: A string representing a URL. The initializer crashes if the string
    ///                           is not a valid URL.
    init(staticString: StaticString) {
        // swiftlint:disable:next force_unwrapping
        self.init(string: String(describing: staticString))!
    }

    /// Returns the path component of the URL; otherwise it returns an empty string. Doesn't do percent
    /// encoding.
    ///
    /// - returns: The path component of the URL.
    func normalizedPath() -> String? {
        return if #available(iOS 17.1, *) {
            // The method was introduced in iOS 16.0, but we observed it crashing on iOS 16 (up to 16.7.x)
            // and iOS 17.0.x (up to 17.0.3).
            self.path(percentEncoded: false)
        } else {
            self.relativePath
        }
    }
}
