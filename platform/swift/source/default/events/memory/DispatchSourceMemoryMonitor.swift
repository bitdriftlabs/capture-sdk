// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

final class DispatchSourceMemoryMonitor {
    private let logger: CoreLogging
    private let memorySnapshotProvider: MemorySnapshotProvider
    private let dispatchSource: MemoryPressureSourceProvider

    convenience init(logger: CoreLogging) {
        self.init(
            logger: logger,
            dispatchSource: DispatchMemoryPressureSourceAdapter(),
            snapshotProvider: MemorySnapshotProvider()
        )
    }

    init(
        logger: CoreLogging,
        dispatchSource: MemoryPressureSourceProvider,
        snapshotProvider: MemorySnapshotProvider
    ) {
        self.logger = logger
        self.memorySnapshotProvider = snapshotProvider
        self.memorySnapshotProvider.logger = logger
        self.dispatchSource = dispatchSource
        // Set the event handler, but don't enable until `start()` is called.
        self.dispatchSource.setEventHandler { [weak self] in
            self?.maybeSnapshot()
        }
        #if DEBUG
        NotificationCenter.default.addObserver(
            forName: .captureSimulateMemoryPressure,
            object: nil,
            queue: nil
        ) { [weak self] notification in
            guard let self else { return }
            let level = notification.userInfo?["level"] as? Int8 ?? 0
            self.logger.notifyMemoryPressure(level: MemoryPressureLevel(rawValue: level) ?? .unknown)
        }
        #endif
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

        let event = self.dispatchSource.data

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

        self.logger.notifyMemoryPressure(level: MemoryPressureLevel.from(event))
    }
}

extension MemoryPressureLevel {
    static func from(
        _ memoryPressureEvent: DispatchSource.MemoryPressureEvent
    ) -> MemoryPressureLevel {
        switch memoryPressureEvent {
        case .normal: return .normal
        case .warning: return .warning
        case .critical: return .critical
        default: return .unknown
        }
    }
}

#if DEBUG
extension Notification.Name {
    static let captureSimulateMemoryPressure = Notification.Name(
        "io.bitdrift.capture.simulate_memory_pressure"
    )
}
#endif
