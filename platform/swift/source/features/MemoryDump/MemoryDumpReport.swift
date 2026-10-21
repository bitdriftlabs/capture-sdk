// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// The JSON attachment of an `ios_memory_dump` invocation: one section per stage that ran.
struct MemoryDumpReport: Encodable, Equatable {
    static let currentSchemaVersion = 2

    let schemaVersion = Self.currentSchemaVersion
    var counters: VirtualMemoryCounters.Report?
    var regions: RegionWalk.Report?
    var heap: HeapCensus.Report?

    func encoded() throws -> Data {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(self)
    }
}
