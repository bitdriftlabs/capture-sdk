// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureMemoryCore

/// Process VM regions, aggregated by Mach tag and by owning image.
enum RegionWalk {
    struct Section: Encodable, Equatable {
        let mode: String
        let regions: UInt32
        let truncated: Bool
        let privateDirty: UInt64
        let sharedDirty: UInt64
        let swapped: UInt64
        /// What weighs on the footprint: private dirty plus swapped bytes.
        let charged: UInt64
        let virtualSize: UInt64
        let byTag: [TagRow]
        let byImage: [ImageRow]
    }

    struct TagRow: Encodable, Equatable {
        let name: String
        /// only `nil` for the `(other)` row.
        let tag: UInt32?
        var regions: UInt32
        var privateDirty: UInt64
        var sharedDirty: UInt64
        var swapped: UInt64
        var virtualSize: UInt64
        var charged: UInt64 { self.privateDirty + self.swapped }

        private enum CodingKeys: String, CodingKey {
            case name, tag, regions, privateDirty, sharedDirty, swapped, virtualSize, charged
        }

        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.name, forKey: .name)
            try container.encodeIfPresent(self.tag, forKey: .tag)
            try container.encode(self.regions, forKey: .regions)
            try container.encode(self.privateDirty, forKey: .privateDirty)
            try container.encode(self.sharedDirty, forKey: .sharedDirty)
            try container.encode(self.swapped, forKey: .swapped)
            try container.encode(self.virtualSize, forKey: .virtualSize)
            try container.encode(self.charged, forKey: .charged)
        }
    }

    struct ImageRow: Encodable, Equatable {
        let name: String
        var charged: UInt64
    }

    static let otherRowName = "(other)"

    static func capture(_ options: MemoryDumpRequest.RegionsOptions) throws(CommandError) -> Section {
        let images = bdmem_image_table_create()
        defer { bdmem_image_table_destroy(images) }

        var chargedByImage = [UInt64](repeating: 0, count: Int(bdmem_image_table_image_count(images)))
        var result = bdmem_regions_result_t()
        let succeeded = chargedByImage.withUnsafeMutableBufferPointer { buffer in
            result.charged_by_image = buffer.baseAddress
            result.image_count = UInt32(buffer.count)
            return bdmem_regions_walk(Self.walkMode(options.mode), images, &result)
        }
        guard succeeded else {
            throw CommandError(title: "Unable to walk VM regions")
        }

        let tagRows = Self.tagRows(from: &result)
        let limit = options.limit.map { Int(clamping: $0) }
        return Section(
            mode: options.mode.rawValue,
            regions: result.region_count,
            truncated: result.truncated,
            privateDirty: tagRows.reduce(0) { $0 + $1.privateDirty },
            sharedDirty: tagRows.reduce(0) { $0 + $1.sharedDirty },
            swapped: tagRows.reduce(0) { $0 + $1.swapped },
            charged: tagRows.reduce(0) { $0 + $1.charged },
            virtualSize: tagRows.reduce(0) { $0 + $1.virtualSize },
            byTag: Self.keepingTop(limit, of: tagRows),
            byImage: Self.keepingTop(limit, of: Self.imageRows(images: images, charged: chargedByImage))
        )
    }

    private static func walkMode(_ mode: MemoryDumpRequest.RegionsMode) -> bdmem_regions_mode_t {
        switch mode {
        case .layout:
            BDMEM_REGIONS_LAYOUT
        case .writable:
            BDMEM_REGIONS_WRITABLE
        case .full:
            BDMEM_REGIONS_FULL
        }
    }

    private static func tagRows(from result: inout bdmem_regions_result_t) -> [TagRow] {
        withUnsafeBytes(of: &result.by_tag) { raw in
            raw.bindMemory(to: bdmem_tag_totals_t.self).enumerated().compactMap { tag, totals in
                guard totals.regions > 0 else {
                    return nil
                }
                return TagRow(
                    name: bdmem_tag_name(UInt32(tag)).map { String(cString: $0) } ?? "VM_TAG_\(tag)",
                    tag: UInt32(tag),
                    regions: totals.regions,
                    privateDirty: totals.private_dirty,
                    sharedDirty: totals.shared_dirty,
                    swapped: totals.swapped,
                    virtualSize: totals.virtual_size
                )
            }
        }
        .sorted { ($0.charged, $0.virtualSize) > ($1.charged, $1.virtualSize) }
    }

    private static func imageRows(images: OpaquePointer?, charged: [UInt64]) -> [ImageRow] {
        var chargedByName: [String: UInt64] = [:]
        for (index, bytes) in charged.enumerated() where bytes > 0 {
            guard let rawPath = bdmem_image_table_image_name(images, UInt32(index)) else {
                continue
            }
            // Only the binary's name: paths carry the app container and user directories.
            let path = String(cString: rawPath)
            let name = path.split(separator: "/").last.map(String.init) ?? path
            chargedByName[name, default: 0] += bytes
        }
        return chargedByName
            .map { ImageRow(name: $0.key, charged: $0.value) }
            .sorted { ($0.charged, $1.name) > ($1.charged, $0.name) }
    }

    private static func keepingTop(_ limit: Int?, of rows: [TagRow]) -> [TagRow] {
        guard let limit, rows.count > limit else {
            return rows
        }
        var other = TagRow(
            name: Self.otherRowName,
            tag: nil,
            regions: 0,
            privateDirty: 0,
            sharedDirty: 0,
            swapped: 0,
            virtualSize: 0
        )
        for row in rows[limit...] {
            other.regions += row.regions
            other.privateDirty += row.privateDirty
            other.sharedDirty += row.sharedDirty
            other.swapped += row.swapped
            other.virtualSize += row.virtualSize
        }
        return Array(rows.prefix(limit)) + [other]
    }

    private static func keepingTop(_ limit: Int?, of rows: [ImageRow]) -> [ImageRow] {
        guard let limit, rows.count > limit else {
            return rows
        }
        let other = rows[limit...].reduce(0) { $0 + $1.charged }
        return Array(rows.prefix(limit)) + [ImageRow(name: Self.otherRowName, charged: other)]
    }
}
