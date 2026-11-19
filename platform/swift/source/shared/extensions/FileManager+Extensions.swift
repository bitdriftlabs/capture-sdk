// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

enum DirectorySizeError: Error {
    case cantEnumerateDirectory
}

private let kAllocatedSizeResourceKeys: Set<URLResourceKey> = [
    .isRegularFileKey,
    .fileAllocatedSizeKey,
    .totalFileAllocatedSizeKey,
]

extension FileManager {
    /// Calculate the allocated size of a directory and all its contents on the given path.
    ///
    /// At the moment, APFS functions to get this information quickly are private (see dirstat.h).
    ///
    /// - parameter directoryURL: The URL of a directory to get the size of.
    ///
    /// - returns: The measured size of the directory. Expressed in bytes.
    func allocatedSizeOfDirectory(at directoryURL: URL) throws -> UInt64 {
        let enumerator = self.enumerator(at: directoryURL,
                                         includingPropertiesForKeys: Array(kAllocatedSizeResourceKeys),
                                         options: [],
                                         errorHandler: nil)

        guard let enumerator else {
            throw DirectorySizeError.cantEnumerateDirectory
        }

        return try enumerator.reduce(0) { partialResult, item in
            // swiftlint:disable:next force_cast
            return try partialResult + (item as! URL).regularFileAllocatedSize()
        }
    }
}

private extension URL {
    func regularFileAllocatedSize() throws -> UInt64 {
        let resourceValues = try self.resourceValues(forKeys: kAllocatedSizeResourceKeys)
        guard resourceValues.isRegularFile ?? false else {
            return 0
        }

        return UInt64(resourceValues.totalFileAllocatedSize ?? resourceValues.fileAllocatedSize ?? 0)
    }
}
