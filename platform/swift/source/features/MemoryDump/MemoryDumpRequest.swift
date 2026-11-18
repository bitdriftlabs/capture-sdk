// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/// The parsed arguments of a `memory_dump` invocation.
struct MemoryDumpRequest: Equatable {
    /// The stages a memory dump can run, declared in execution order.
    enum Stage: String, CaseIterable {
        case counters
        case regions
        case heap
    }

    let includeHeap: Bool

    init(includeHeap: Bool = true) {
        self.includeHeap = includeHeap
    }

    /// The stages to run, in execution order.
    var stages: [Stage] {
        self.includeHeap ? Stage.allCases : [.counters, .regions]
    }

    /// Parses the command's arguments.
    ///
    /// - parameter arguments: The invocation arguments.
    ///
    /// - throws: An invalid-arguments `CommandError` for an unknown argument or a value of the wrong
    ///           type.
    init(arguments: CommandArguments) throws(CommandError) {
        var includeHeap = true

        for (name, value) in arguments {
            switch name {
            case "include_heap":
                guard case let .bool(flag) = value else {
                    throw .invalidArguments(description: "Argument '\(name)' must be a bool.")
                }
                includeHeap = flag
            default:
                throw .invalidArguments(description: "Unknown argument '\(name)'.")
            }
        }

        self.init(includeHeap: includeHeap)
    }
}
