// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

final class DispatchSourceMemoryMonitor {
    // Monitor all state changes.
    private let dispatchSource = DispatchSource.makeMemoryPressureSource(
        eventMask: .all,
        queue: .serial(withLabelSuffix: "DispatchSourceMemoryMonitor", target: .default)
    )

    private let memorySnapshotProvider = MemorySnapshotProvider()

    private let logger: CoreLogging

    init(logger: CoreLogging) {
        self.logger = logger
        self.memorySnapshotProvider.logger = logger
        // Set the event handler, but don't enable until `start()` is called.
        self.dispatchSource.setEventHandler { [weak self] in
            self?.maybeSnapshot()
        }
    }

    deinit {
        self.stop()
    }

    // MARK: - MemoryStateMonitor

    func start() {
        self.dispatchSource.activate()
    }

    func stop() {
        self.dispatchSource.cancel()
    }

    // MARK: - Private

    private func maybeSnapshot() {
        if !self.logger.runtimeValue(.memoryStateChangeReporting) {
            // Return early and don't snapshot if the feature is explicitly disabled.
            return
        }

        guard !self.dispatchSource.isCancelled else {
            return
        }

        let event = DispatchSource.MemoryPressureEvent(rawValue: self.dispatchSource.data)

        let state: String
        switch event {
        case .warning:
            state = "warning"
        case .critical:
            state = "critical"
        case .normal:
            state = "normal"
        default:
            state = "unknown: \(event.rawValue)"
        }

        let snapshot = self.memorySnapshotProvider.makeSnapshot()

        var fields = snapshot?.toDictionary() ?? [:]
        fields["_mem_level"] = state

        self.logger.log(
            level: .info,
            message: "AppMemPressure",
            fields: fields,
            error: nil,
            type: .lifecycle
        )
    }
}
