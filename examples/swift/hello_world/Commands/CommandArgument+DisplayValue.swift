// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture

extension CommandArgument {
    var displayValue: String {
        switch self {
        case let .string(value): return value
        case let .binary(value): return "\(value.count) bytes"
        case let .uint64(value): return String(value)
        case let .double(value): return String(value)
        case let .int64(value): return String(value)
        case let .bool(value): return String(value)
        @unknown default:
            return "Error"
        }
    }
}
