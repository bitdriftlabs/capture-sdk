// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/// The parsed arguments of an `ios_memory_dump` invocation.
struct MemoryDumpRequest: Equatable {
    /// The stages a memory dump can run, declared in execution order.
    enum Stage: String, CaseIterable {
        case counters
        case regions
        case heap
    }

    enum RegionsMode: String, Equatable {
        case layout
        case writable
        case full
    }

    struct RegionsOptions: Equatable {
        var mode: RegionsMode = .writable
        var limit: UInt64?
    }

    struct HeapOptions: Equatable {
        var budgetMs: UInt64 = 250
        var limit: UInt64?
        var owners = true
        var webkit = false
    }

    static let maximumHeapBudgetMs: UInt64 = 4_000

    /// The requested stages, in execution order and without duplicates.
    let stages: [Stage]
    let regions: RegionsOptions
    let heap: HeapOptions

    init(stages: [Stage] = Stage.allCases, regions: RegionsOptions = .init(), heap: HeapOptions = .init()) {
        self.stages = Stage.allCases.filter(stages.contains)
        self.regions = regions
        self.heap = heap
    }

    /// Parses the command's flat arguments.
    ///
    /// - parameter arguments: The invocation arguments.
    ///
    /// - throws: An invalid-arguments `CommandError` for an unknown argument, a value of the wrong
    ///           type, or a value outside its accepted range.
    init(arguments: CommandArguments) throws(CommandError) {
        var stages = Stage.allCases
        var regions = RegionsOptions()
        var heap = HeapOptions()

        for (name, value) in arguments {
            switch name {
            case "stages":
                stages = try Self.parseStages(try Self.string(value, name: name))
            case "regions.mode":
                let rawMode = try Self.string(value, name: name)
                guard let mode = RegionsMode(rawValue: rawMode) else {
                    throw .invalidArguments(description: "Unknown regions.mode '\(rawMode)'.")
                }
                regions.mode = mode
            case "regions.limit":
                regions.limit = try Self.positive(value, name: name)
            case "heap.budget_ms":
                heap.budgetMs = min(try Self.uint64(value, name: name), Self.maximumHeapBudgetMs)
            case "heap.limit":
                heap.limit = try Self.positive(value, name: name)
            case "heap.owners":
                heap.owners = try Self.bool(value, name: name)
            case "heap.webkit":
                heap.webkit = try Self.bool(value, name: name)
            default:
                throw .invalidArguments(description: "Unknown argument '\(name)'.")
            }
        }

        self.init(stages: stages, regions: regions, heap: heap)
    }

    private static func parseStages(_ rawStages: String) throws(CommandError) -> [Stage] {
        var stages: [Stage] = []
        for rawStage in rawStages.split(separator: ",", omittingEmptySubsequences: false) {
            let name = rawStage.trimmingCharacters(in: .whitespaces)
            guard let stage = Stage(rawValue: name) else {
                throw .invalidArguments(description: "Unknown stage '\(name)'.")
            }
            stages.append(stage)
        }
        return stages
    }

    private static func string(_ value: CommandArgument, name: String) throws(CommandError) -> String {
        guard case let .string(string) = value else {
            throw typeMismatch(name: name, expected: "string")
        }
        return string
    }

    private static func uint64(_ value: CommandArgument, name: String) throws(CommandError) -> UInt64 {
        guard case let .uint64(number) = value else {
            throw typeMismatch(name: name, expected: "uint64")
        }
        return number
    }

    private static func positive(_ value: CommandArgument, name: String) throws(CommandError) -> UInt64 {
        let number = try uint64(value, name: name)
        guard number > 0 else {
            throw .invalidArguments(description: "Argument '\(name)' must be greater than zero.")
        }
        return number
    }

    private static func bool(_ value: CommandArgument, name: String) throws(CommandError) -> Bool {
        guard case let .bool(flag) = value else {
            throw typeMismatch(name: name, expected: "bool")
        }
        return flag
    }

    private static func typeMismatch(name: String, expected: String) -> CommandError {
        .invalidArguments(description: "Argument '\(name)' must be a \(expected).")
    }
}
