// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// A flat list of OTLP attributes (used for both span and resource attributes), encoded as a
/// single string so it can cross the Rust bridge without marshaling `NSArray`.
///
/// Encoding: attributes are separated by U+001E (record separator); each attribute's
/// `key`/`type`/`value` are separated by U+001F (unit separator). `type` is `"s"`/`"i"`/`"b"` for
/// string/int/bool. Must match `parse_otlp_attributes` in `platform/swift/source/src/bridge.rs`.
/// Adding a new attribute is therefore a pure Swift change that never touches the bridge
/// signature.
final class OtelAttributes {
    private(set) var encoded = ""

    func add(_ key: String, _ value: String) {
        self.append(key: key, type: "s", value: value)
    }

    func add(_ key: String, _ value: Int) {
        self.append(key: key, type: "i", value: String(value))
    }

    func add(_ key: String, _ value: Bool) {
        self.append(key: key, type: "b", value: value ? "true" : "false")
    }

    private func append(key: String, type: Character, value: String) {
        if !self.encoded.isEmpty {
            self.encoded.append("\u{1e}")
        }

        self.encoded.append(key)
        self.encoded.append("\u{1f}")
        self.encoded.append(type)
        self.encoded.append("\u{1f}")
        self.encoded.append(value)
    }
}
