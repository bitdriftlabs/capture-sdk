// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureMemoryCore
import Darwin

/// Stage 3: the malloc heap's live blocks, grouped by type and by owning image.
enum HeapCensus {
    struct Section: Encodable, Equatable {
        /// The lock budget ran out: the figures are a floor, not an inventory.
        let truncated: Bool
        let totalBlocks: UInt64
        let totalBytes: UInt64
        let namedBlocks: UInt64
        let namedBytes: UInt64
        let swiftBoxBlocks: UInt64
        let swiftBoxBytes: UInt64
        let imageBlocks: UInt64
        let imageBytes: UInt64
        let unclassifiedBlocks: UInt64
        let unclassifiedBytes: UInt64
        /// Unclassified blocks per power-of-two size: index `n` counts sizes in [2^n, 2^(n+1)).
        let unclassifiedHistogram: [UInt64]
        let largestBlockBytes: UInt64
        let skippedBlocks: UInt64
        let candidateOverflow: UInt64
        let unreadableCandidates: UInt64
        /// Unclassified blocks credited to the named object pointing at them. A subset of
        /// `unclassified*`, never an addition to it.
        let ownedBlocks: UInt64
        let ownedBytes: UInt64
        let sharedOwnedBlocks: UInt64
        let ownerTableOverflow: UInt64
        let lockHeldMs: Double
        let lockWaitMs: Double
        let resolveMs: Double
        let ownersMs: Double
        let byType: [TypeRow]
        /// Types by the unclassified bytes they own, empty when owners are not attributed.
        let byOwner: [TypeRow]
        let byImage: [ImageRow]
        let zones: [ZoneRow]

        /// Zones that were enumerated without ever reporting a batch.
        var zonesNotSeen: [String] {
            self.zones.filter { $0.enumerated && !$0.seen }.map(\.name)
        }

        var zonesSkipped: [String] {
            self.zones.filter(\.skipped).map(\.name)
        }
    }

    struct TypeRow: Encodable, Equatable {
        enum Kind: String, Encodable {
            case `class`
            case cxx
        }

        let name: String
        /// `nil` for the `(other)` row.
        let kind: Kind?
        var blocks: UInt64
        var bytes: UInt64
    }

    struct ImageRow: Encodable, Equatable {
        let name: String
        var blocks: UInt64
        var bytes: UInt64
    }

    struct ZoneRow: Encodable, Equatable {
        let name: String
        let blocks: UInt64
        let bytes: UInt64
        let lockHeldMs: Double
        let enumerated: Bool
        let seen: Bool
        let skipped: Bool
        let truncated: Bool
    }

    static let otherRowName = "(other)"

    /// Its lock was held for 9.9 s with a web view open, and its enumerator reported nothing.
    static let webKitZoneName = "WebKit Malloc"

    static func capture(_ options: MemoryDumpRequest.HeapOptions, demangle: Bool) throws(CommandError) -> Section {
        let skippedZones = options.webkit ? [] : [Self.webKitZoneName]
        let census = try Self.makeCensus(options, skippedZones: skippedZones)
        defer { bdmem_census_destroy(census) }

        guard bdmem_census_run(census), let summary = bdmem_census_summary(census)?.pointee else {
            throw CommandError(title: "Unable to run the heap census")
        }

        let (types, owners, images) = Self.rows(of: census, demangle: demangle)
        let limit = options.limit.map { Int(clamping: $0) }
        return Section(
            truncated: summary.truncated,
            totalBlocks: summary.total_blocks,
            totalBytes: summary.total_bytes,
            namedBlocks: summary.named_blocks,
            namedBytes: summary.named_bytes,
            swiftBoxBlocks: summary.swift_box_blocks,
            swiftBoxBytes: summary.swift_box_bytes,
            imageBlocks: summary.image_blocks,
            imageBytes: summary.image_bytes,
            unclassifiedBlocks: summary.unclassified_blocks,
            unclassifiedBytes: summary.unclassified_bytes,
            unclassifiedHistogram: withUnsafeBytes(of: summary.unclassified_histogram) {
                Array($0.bindMemory(to: UInt64.self))
            },
            largestBlockBytes: summary.largest_block_bytes,
            skippedBlocks: summary.skipped_blocks,
            candidateOverflow: summary.candidate_overflow,
            unreadableCandidates: summary.unreadable_candidates,
            ownedBlocks: summary.owned_blocks,
            ownedBytes: summary.owned_bytes,
            sharedOwnedBlocks: summary.shared_owned_blocks,
            ownerTableOverflow: summary.owner_table_overflow,
            lockHeldMs: Self.milliseconds(summary.lock_held_ns),
            lockWaitMs: Self.milliseconds(summary.lock_wait_ns),
            resolveMs: Self.milliseconds(summary.resolve_ns),
            ownersMs: Self.milliseconds(summary.owners_ns),
            byType: Self.keepingTop(limit, of: types),
            byOwner: Self.keepingTop(limit, of: owners),
            byImage: Self.keepingTop(limit, of: images),
            zones: Self.zoneRows(of: summary)
        )
    }

    private static func makeCensus(
        _ options: MemoryDumpRequest.HeapOptions,
        skippedZones: [String]
    ) throws(CommandError) -> OpaquePointer {
        // The census copies the names, so they only need to outlive creation.
        let names = skippedZones.map { strdup($0) }
        defer { names.forEach { free($0) } }

        let census = names.map { UnsafePointer($0) }.withUnsafeBufferPointer { buffer in
            var censusOptions = bdmem_census_options_t()
            censusOptions.budget_ms = options.budgetMs
            censusOptions.attribute_owners = options.owners
            censusOptions.skipped_zone_names = buffer.baseAddress
            censusOptions.skipped_zone_count = UInt32(buffer.count)
            return bdmem_census_create(&censusOptions)
        }
        guard let census else {
            throw CommandError(title: "Unable to create the heap census")
        }
        return census
    }

    private static func rows(
        of census: OpaquePointer,
        demangle: Bool
    ) -> (types: [TypeRow], owners: [TypeRow], images: [ImageRow]) {
        var types: [String: TypeRow] = [:]
        var owners: [String: TypeRow] = [:]
        var images: [String: ImageRow] = [:]

        var entry = bdmem_type_entry_t()
        for index in 0..<bdmem_census_type_count(census) {
            guard bdmem_census_type_at(census, index, &entry), let rawName = entry.name else {
                continue
            }
            let name = String(cString: rawName)

            switch entry.kind {
            case BDMEM_TYPE_CLASS, BDMEM_TYPE_CXX:
                let kind: TypeRow.Kind = entry.kind == BDMEM_TYPE_CLASS ? .class : .cxx
                let readable = kind == .class
                    ? SymbolNames.swiftClassName(name, classPointer: UInt(entry.class_pointer), demangle: demangle)
                    : SymbolNames.cxxTypeName(name, demangle: demangle)
                // Types whose readable names collide are reported together.
                let key = "\(kind.rawValue)|\(readable)"
                types[key, default: TypeRow(name: readable, kind: kind, blocks: 0, bytes: 0)].blocks += entry.blocks
                types[key]?.bytes += entry.bytes
                if entry.owned_blocks > 0 {
                    owners[key, default: TypeRow(name: readable, kind: kind, blocks: 0, bytes: 0)].blocks +=
                        entry.owned_blocks
                    owners[key]?.bytes += entry.owned_bytes
                }
            case BDMEM_TYPE_IMAGE:
                // Only the binary's name: paths carry the app container and user directories.
                let binary = name.split(separator: "/").last.map(String.init) ?? name
                images[binary, default: ImageRow(name: binary, blocks: 0, bytes: 0)].blocks += entry.blocks
                images[binary]?.bytes += entry.bytes
            default:
                continue
            }
        }

        return (
            types.values.sorted { ($0.bytes, $1.name) > ($1.bytes, $0.name) },
            owners.values.sorted { ($0.bytes, $1.name) > ($1.bytes, $0.name) },
            images.values.sorted { ($0.bytes, $1.name) > ($1.bytes, $0.name) }
        )
    }

    private static func zoneRows(of summary: bdmem_census_summary_t) -> [ZoneRow] {
        withUnsafeBytes(of: summary.zones) { raw in
            raw.bindMemory(to: bdmem_zone_summary_t.self)
                .prefix(Int(summary.zone_count))
                .map { zone in
                    var name = zone.name
                    return ZoneRow(
                        name: withUnsafeBytes(of: &name) { String(cString: $0.bindMemory(to: CChar.self).baseAddress!) },
                        blocks: zone.blocks,
                        bytes: zone.bytes,
                        lockHeldMs: Self.milliseconds(zone.lock_held_ns),
                        enumerated: zone.enumerated,
                        seen: zone.seen,
                        skipped: zone.skipped,
                        truncated: zone.truncated
                    )
                }
        }
    }

    private static func keepingTop(_ limit: Int?, of rows: [TypeRow]) -> [TypeRow] {
        guard let limit, rows.count > limit else {
            return rows
        }
        let rest = rows[limit...]
        let other = TypeRow(
            name: Self.otherRowName,
            kind: nil,
            blocks: rest.reduce(0) { $0 + $1.blocks },
            bytes: rest.reduce(0) { $0 + $1.bytes }
        )
        return Array(rows.prefix(limit)) + [other]
    }

    private static func keepingTop(_ limit: Int?, of rows: [ImageRow]) -> [ImageRow] {
        guard let limit, rows.count > limit else {
            return rows
        }
        let rest = rows[limit...]
        let other = ImageRow(
            name: Self.otherRowName,
            blocks: rest.reduce(0) { $0 + $1.blocks },
            bytes: rest.reduce(0) { $0 + $1.bytes }
        )
        return Array(rows.prefix(limit)) + [other]
    }

    private static func milliseconds(_ nanoseconds: UInt64) -> Double {
        (Double(nanoseconds) / 1_000).rounded() / 1_000
    }
}
