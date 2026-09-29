// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

public typealias CommandArguments = [String: CommandArgument]

public extension Dictionary where Key == String, Value == CommandArgument {
    /// Returns the string value for `key`, or `nil` when it is absent or has another type.
    func string(for key: String) -> String? {
        guard case let .string(value) = self[key] else {
            return nil
        }
        return value
    }
}
