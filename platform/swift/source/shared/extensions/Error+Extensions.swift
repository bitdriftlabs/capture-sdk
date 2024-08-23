// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

extension Error {
    /// Converts receiver to fields map.
    ///
    /// - returns: Fields map.
    func toFields() -> [String: Encodable] {
        var fields = [String: Encodable]()

        fields["_error"] = self.localizedDescription
        fields["_error_details"] = String(describing: self)

        for (key, value) in (self as NSError).userInfo {
            guard let value = value as? Encodable else {
                continue
            }

            fields["_error_info_\(key)"] = value
        }

        return fields
    }
}
