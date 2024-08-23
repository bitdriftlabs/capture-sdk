// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CapturePassable
import Foundation

final class ResourceUtilizationTarget {
    private let queue: DispatchQueue = .serial(withLabelSuffix: "ResourceUtilizationTarget", target: .heavy)

    private let storageProvider: StorageProvider
    private let timeProvider: TimeProvider

    private let diskUsageProvider: DiskUsageProvider

    let providers: [ResourceSnapshotProvider]
    var logger: CoreLogging? {
        didSet {
            self.diskUsageProvider.logger = self.logger
        }
    }

    init(storageProvider: StorageProvider, timeProvider: TimeProvider) {
        self.storageProvider = storageProvider
        self.timeProvider = timeProvider

        self.diskUsageProvider = DiskUsageProvider(
            storageProvider: storageProvider,
            timeProvider: timeProvider
        )

        self.providers = [
            MemorySnapshotProvider(),
            BatterySnapshotProvider(),
            LowPowerStateProvider(),
            self.diskUsageProvider,
        ]
    }
}

extension ResourceUtilizationTarget: CapturePassable.ResourceUtilizationTarget {
    func tick() {
        self.queue.async { [weak logger] in
            let start = self.timeProvider.uptime()

            var fields: Fields

            do {
                fields = try self.providers
                    .compactMap { try $0.makeSnapshot()?.toDictionary() }
                    .reduce(into: [:]) { accumulator, current in
                        accumulator.mergeOverwritingConflictingKeys(current)
                    }
            } catch let error {
                fields = [:]
                self.logger?.handleError(
                    context: "ResourceUtilizationTarget",
                    error: error
                )
            }

            logger?.logResourceUtilization(
                fields: fields,
                duration: self.timeProvider.timeIntervalSince(start)
            )
        }
    }
}
