// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/// Forwards command lifecycle updates to the thread-safe Rust logger handle.
final class CommandRegistrationBridge: CommandRegistrationBridging, @unchecked Sendable {
    private let logger: CoreLogging

    init(logger: CoreLogging) {
        self.logger = logger
    }

    func registerCommand(key: String, target: CommandsTarget) {
        logger.registerCommand(key: key, target: target)
    }

    func unregisterCommand(key: String) {
        logger.unregisterCommand(key: key)
    }
}
