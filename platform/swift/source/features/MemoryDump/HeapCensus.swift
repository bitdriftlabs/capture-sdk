// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureMemoryCore
import Darwin

/// Malloc heap's live blocks, grouped by type and by owning image.
enum HeapCensus {
    struct Report: Encodable, Equatable {
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

    enum Kind: String, Encodable {
        case `class`
        case cxx
        case image
    }

    struct TypeRow: Encodable, Equatable {
        let name: String
        let kind: Kind
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

    static func capture(
        _ options: MemoryDumpRequest.HeapOptions,
        images: ImageTable?,
        demangle: Bool
    ) throws(CommandError) -> Report {
        var censusOptions = bdmem_census_options_t()
        censusOptions.budget_ms = options.budgetMs
        censusOptions.attribute_owners = options.owners
        guard let census = bdmem_census_create(&censusOptions, images?.pointer) else {
            throw CommandError(title: "Unable to create the heap census")
        }
        defer { bdmem_census_destroy(census) }

        guard bdmem_census_run(census), let summary = bdmem_census_summary(census)?.pointee else {
            throw CommandError(title: "Unable to run the heap census")
        }

        let (types, owners, images) = Self.rows(of: census, demangle: demangle)
        return Report(
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
            byType: types,
            byOwner: owners,
            byImage: images,
            zones: Self.zoneRows(of: summary)
        )
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

            guard let kind = Self.kind(entry.kind) else {
                continue
            }
            switch kind {
            case .class, .cxx:
                let readable = Self.readableName(
                    name,
                    kind: kind,
                    classPointer: entry.class_pointer,
                    demangle: demangle
                )
                // Types whose readable names collide are reported together.
                let key = "\(kind.rawValue)|\(readable)"
                types[key, default: TypeRow(name: readable, kind: kind, blocks: 0, bytes: 0)].blocks += entry.blocks
                types[key]?.bytes += entry.bytes
                if entry.owned_blocks > 0 {
                    owners[key, default: TypeRow(name: readable, kind: kind, blocks: 0, bytes: 0)].blocks +=
                        entry.owned_blocks
                    owners[key]?.bytes += entry.owned_bytes
                }
            case .image:
                let binary = ImageTable.binaryName(name)
                images[binary, default: ImageRow(name: binary, blocks: 0, bytes: 0)].blocks += entry.blocks
                images[binary]?.bytes += entry.bytes
            }
        }

        return (
            types.values.sorted { ($0.bytes, $1.name) > ($1.bytes, $0.name) },
            owners.values.sorted { ($0.bytes, $1.name) > ($1.bytes, $0.name) },
            images.values.sorted { ($0.bytes, $1.name) > ($1.bytes, $0.name) }
        )
    }

    private static func readableName(_ name: String, kind: Kind, classPointer: UInt, demangle: Bool) -> String {
        switch kind {
        case .class:
            SymbolNames.swiftClassName(name, classPointer: classPointer, demangle: demangle)
        case .cxx:
            SymbolNames.cxxTypeName(name, demangle: demangle)
        case .image:
            ImageTable.binaryName(name)
        }
    }

    private static func kind(_ kind: bdmem_type_kind_t) -> Kind? {
        switch kind {
        case BDMEM_TYPE_CLASS:
            .class
        case BDMEM_TYPE_CXX:
            .cxx
        case BDMEM_TYPE_IMAGE:
            .image
        default:
            nil
        }
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

    private static func milliseconds(_ nanoseconds: UInt64) -> Double {
        (Double(nanoseconds) / 1_000).rounded() / 1_000
    }
}
