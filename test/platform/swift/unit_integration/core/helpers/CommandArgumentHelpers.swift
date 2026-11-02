// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

@_spi(ExperimentalAPI) @testable import Capture
import Foundation

protocol CommandArgumentConvertible: Equatable, Hashable {
    func asArgument() -> CommandArgument
}

extension Bool: CommandArgumentConvertible {
    func asArgument() -> CommandArgument {
        return CommandArgument.bool(self)
    }
}

extension String: CommandArgumentConvertible {
    func asArgument() -> CommandArgument {
        return CommandArgument.string(self)
    }
}

extension UInt64: CommandArgumentConvertible {
    func asArgument() -> CommandArgument {
        return CommandArgument.uint64(self)
    }
}

extension Int64: CommandArgumentConvertible {
    func asArgument() -> CommandArgument {
        return CommandArgument.int64(self)
    }
}

extension Data: CommandArgumentConvertible {
    func asArgument() -> CommandArgument {
        return CommandArgument.binary(self)
    }
}

extension Double: CommandArgumentConvertible {
    func asArgument() -> CommandArgument {
        return CommandArgument.double(self)
    }
}
