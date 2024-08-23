// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct DiskUsageSnapshot {
    let documentsDirectorySizeBytes: UInt64
    let cacheDirectorySizeBytes: UInt64
    let temporaryDirectorySizeBytes: UInt64
}

extension DiskUsageSnapshot: ResourceSnapshot {
    enum FieldKey: String {
        case documentsDirectory = "_documents_dir_size_bytes"
        case cacheDirectory = "_cache_dir_size_bytes"
        case temporaryDirectory = "_tmp_dir_size_bytes"
    }

    func toDictionary() -> [String: String] {
        return [
            FieldKey.documentsDirectory.rawValue: String(self.documentsDirectorySizeBytes),
            FieldKey.cacheDirectory.rawValue: String(self.cacheDirectorySizeBytes),
            FieldKey.temporaryDirectory.rawValue: String(self.temporaryDirectorySizeBytes),
        ]
    }
}
