// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import os

/// A simple wrapper around os logging APIs.
final class OSLogger {
    private let logger: os.Logger

    /// Initializes a new logger.
    ///
    /// - parameter subsystem: The name of the system the logger should be initialized with.
    ///                        It helps to organize emitted logs. It's prefixed with a "io.bitdrift.capture."
    ///                        string to ensure that all Capture libraries use the same subsystem prefix.
    init(subsystem: String) {
        self.logger = os.Logger(
            subsystem: "io.bitdrift.capture.\(subsystem)",
            category: "logging"
        )
    }

    /// Logs a new message.
    ///
    /// - parameter level:   The log severity level.
    /// - parameter message: The log message.
    func log(level: OSLogType, message: String) {
        self.logger.log(level: level, "\(message)")
    }
}
