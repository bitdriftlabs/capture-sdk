// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

/// A wrapper around platform event listeners that subscribe to various system notifications
/// and emit Capture out-of-the-box in response to them.
final class EventSubscriber {
    private let listeners = Atomic([EventListener]())

    func setUp(
        logger: CoreLogging,
        appStateAttributes: AppStateAttributes,
        clientAttributes: ClientAttributes,
        timeProvider: TimeProvider
    ) {
        self.listeners.update { listeners in
            listeners = [
                LoggerLifecycleController(logger: logger),
                LifecycleEventListener(logger: logger),
                DeviceStateListener(logger: logger),
                AppUpdateEventListener(
                    logger: logger,
                    clientAttributes: clientAttributes,
                    timeProvider: timeProvider
                ),
                ANRReporter(logger: logger, appStateAttributes: appStateAttributes),
            ]
        }
    }

    deinit {
        self.stop()
    }
}

extension EventSubscriber: EventsListenerTarget {
    func start() {
        self.listeners.update { $0.forEach { $0.start() } }
    }

    func stop() {
        self.listeners.update { $0.forEach { $0.stop() } }
    }
}
