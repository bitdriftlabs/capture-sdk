// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

private extension StorageKey<Date> {
    static let lastAppDiskUsageEventEmissionTime = StorageKey<Date>(key: "lastAppDiskUsageEventEmissionTime")
}

private let kDay: TimeInterval = 24 * 60 * 60

/// Responsible for collecting information about disk usage. The information is collected and reported back
/// no more than once every 24 hours.
final class DiskUsageProvider {
    private let storageProvider: StorageProvider
    private let timeProvider: TimeProvider

    var logger: CoreLogging?

    init(storageProvider: StorageProvider, timeProvider: TimeProvider) {
        self.storageProvider = storageProvider
        self.timeProvider = timeProvider
    }
}

extension DiskUsageProvider: ResourceSnapshotProvider {
    func makeSnapshot() throws -> ResourceSnapshot? {
        guard self.logger?.runtimeValue(.diskUsageReporting) == true else {
            return nil
        }

        let has24HoursPassed =
            self.storageProvider
                .get(.lastAppDiskUsageEventEmissionTime)
                .flatMap { self.timeProvider.now().timeIntervalSince($0) > kDay }
                ?? true

        guard has24HoursPassed else {
            return nil
        }

        defer {
            self.storageProvider.set(self.timeProvider.now(), forKey: .lastAppDiskUsageEventEmissionTime)
        }

        return DiskUsageSnapshot(
            documentsDirectorySizeBytes: try FileManager.default.allocatedSizeOfAppDocuments(),
            cacheDirectorySizeBytes: try FileManager.default.allocatedSizeOfCacheDirectory(),
            temporaryDirectorySizeBytes: try FileManager.default.allocatedSizeOfTemporaryDirectory()
        )
    }
}

private extension FileManager {
    /// Calculated the allocated size of the documents directory.
    ///
    /// - returns: The size of documents directory. Expressed in bytes.
    func allocatedSizeOfAppDocuments() throws -> UInt64 {
        guard let url = self.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return 0
        }

        return try self.allocatedSizeOfDirectory(at: url)
    }

    /// Calculate the allocated size of the directory holding all temporary files in the context of the app
    /// sandbox.
    ///
    /// - returns: The size of the temporary directory. Expressed in bytes.
    func allocatedSizeOfTemporaryDirectory() throws -> UInt64 {
        return try self.allocatedSizeOfDirectory(at: self.temporaryDirectory)
    }

    /// Calculate the allocated size of the cache directory.
    ///
    /// - returns: The size of the cache directory. Expressed in bytes.
    func allocatedSizeOfCacheDirectory() throws -> UInt64 {
        guard let url = self.urls(for: .cachesDirectory, in: .userDomainMask).first else {
            return 0
        }

        return try self.allocatedSizeOfDirectory(at: url)
    }
}
