// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

public enum CommandArgument: Equatable, Sendable {
    case string(String)
    case binary(Data)
    case uint64(UInt64)
    case double(Double)
    case int64(Int64)
    case bool(Bool)
}
