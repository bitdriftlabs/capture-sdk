// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

@_spi(ExperimentalAPI) public enum CommandArgument: Equatable, Sendable {
    case string(String)
    case binary(Data)
    case uint64(UInt64)
    case double(Double)
    case int64(Int64)
    case bool(Bool)

    /// Unbox the inner value, whatever it might be!!
    ///
    /// - returns: the value within
    func value() -> Any {
        switch self {
        case .binary(let value):
            return value
        case .bool(let value):
            return value
        case .string(let value):
            return value
        case .double(let value):
            return value
        case .int64(let value):
            return value
        case .uint64(let value):
            return value
        }
    }
}
