// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import UIKit

/// Listens to application lifecycle events and instructs Capture Logger to act accordingly in response
/// to these events.
final class LoggerLifecycleController {
    private var tokens = [NSObjectProtocol]()
    private let logger: CoreLogging
    private let previousRunSentinel: PreviousRunSentinel?

    init(logger: CoreLogging, previousRunSentinel: PreviousRunSentinel? = nil) {
        self.logger = logger
        self.previousRunSentinel = previousRunSentinel
    }
}

extension LoggerLifecycleController: EventListener {
    func start() {
        guard self.tokens.isEmpty else {
            return
        }

        self.tokens.append(NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: nil
        ) { [weak previousRunSentinel] _ in
            previousRunSentinel?.markForeground()
        })

        self.tokens.append(NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: nil
        ) { [weak logger, weak previousRunSentinel] _ in
            /// Flush state in a non-blocking way for cases when it is about to lose its active status.
            /// The idea here is that non-active apps have a higher likelihood of being suspended or killed by
            /// the system.
            previousRunSentinel?.markBackground()
            logger?.flush(blocking: false)
        })

        self.tokens.append(NotificationCenter.default.addObserver(
            forName: UIApplication.willTerminateNotification,
            object: nil,
            queue: nil
        ) { [weak logger, weak previousRunSentinel] _ in
            previousRunSentinel?.clear()
            if logger?.runtimeValue(.loggerFlushingOnForceQuit) == true {
                /// A user force killed the app by swiping it up in "Apps View". We receive this notification
                /// only if the app was active/foregrounded just before it was force killed. Applications that
                /// were backgrounded when they were force killed do not receive this notification.
                logger?.flush(blocking: true)
            }
        })
    }

    func stop() {
        for token in self.tokens {
            NotificationCenter.default.removeObserver(token)
        }
        self.tokens.removeAll()
    }
}
