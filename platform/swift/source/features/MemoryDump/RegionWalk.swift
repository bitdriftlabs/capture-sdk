// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureMemoryCore

/// Process VM regions, aggregated by Mach tag and by owning image.
enum RegionWalk {
    struct Report: Encodable, Equatable {
        let regions: UInt32
        let truncated: Bool
        let privateDirty: UInt64
        let sharedDirty: UInt64
        let compressed: UInt64
        /// What weighs on the footprint: private dirty plus compressed bytes.
        let charged: UInt64
        let virtualSize: UInt64
        let byTag: [TagRow]
        let byImage: [ImageRow]
    }

    struct TagRow: Encodable, Equatable {
        let name: String
        let tag: UInt32
        let regions: UInt32
        let privateDirty: UInt64
        let sharedDirty: UInt64
        let compressed: UInt64
        let virtualSize: UInt64
        var charged: UInt64 { self.privateDirty + self.compressed }

        private enum CodingKeys: String, CodingKey {
            case name, tag, regions, privateDirty, sharedDirty, compressed, virtualSize, charged
        }

        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.name, forKey: .name)
            try container.encode(self.tag, forKey: .tag)
            try container.encode(self.regions, forKey: .regions)
            try container.encode(self.privateDirty, forKey: .privateDirty)
            try container.encode(self.sharedDirty, forKey: .sharedDirty)
            try container.encode(self.compressed, forKey: .compressed)
            try container.encode(self.virtualSize, forKey: .virtualSize)
            try container.encode(self.charged, forKey: .charged)
        }
    }

    struct ImageRow: Encodable, Equatable {
        let name: String
        let charged: UInt64
    }

    static func capture(images: ImageTable?) throws(CommandError) -> Report {
        let imageCount = images.map { Int(bdmem_image_table_image_count($0.pointer)) } ?? 0
        var chargedByImage = [UInt64](repeating: 0, count: imageCount)
        var result = bdmem_regions_result_t()
        let succeeded = chargedByImage.withUnsafeMutableBufferPointer { buffer in
            result.charged_by_image = buffer.baseAddress
            result.image_count = UInt32(buffer.count)
            return bdmem_regions_walk(images?.pointer, &result)
        }
        guard succeeded else {
            throw CommandError(title: "Unable to walk VM regions")
        }

        let tagRows = Self.tagRows(from: &result)
        return Report(
            regions: result.region_count,
            truncated: result.truncated,
            privateDirty: tagRows.reduce(0) { $0 + $1.privateDirty },
            sharedDirty: tagRows.reduce(0) { $0 + $1.sharedDirty },
            compressed: tagRows.reduce(0) { $0 + $1.compressed },
            charged: tagRows.reduce(0) { $0 + $1.charged },
            virtualSize: tagRows.reduce(0) { $0 + $1.virtualSize },
            byTag: tagRows,
            byImage: Self.imageRows(images: images, charged: chargedByImage)
        )
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
                    compressed: totals.compressed,
                    virtualSize: totals.virtual_size
                )
            }
        }
        .sorted { ($0.charged, $0.virtualSize) > ($1.charged, $1.virtualSize) }
    }

    private static func imageRows(images: ImageTable?, charged: [UInt64]) -> [ImageRow] {
        guard let images else {
            return []
        }
        var chargedByName: [String: UInt64] = [:]
        for (index, bytes) in charged.enumerated() where bytes > 0 {
            guard let rawPath = bdmem_image_table_image_name(images.pointer, UInt32(index)) else {
                continue
            }
            chargedByName[ImageTable.binaryName(String(cString: rawPath)), default: 0] += bytes
        }
        return chargedByName
            .map { ImageRow(name: $0.key, charged: $0.value) }
            .sorted { ($0.charged, $1.name) > ($1.charged, $0.name) }
    }
}
