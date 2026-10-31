// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation
import WebKit

struct WebViewLoggingProvider: LoggingProvider {
    private let explicitLogger: Logging?

    init(logger: Logging? = nil) {
        self.explicitLogger = logger
    }

    func getLogging() -> (any Logging)? {
        explicitLogger ?? WebViewIntegration.shared.getLogger()
    }

    func runtimeValue<T: RuntimeValue>(_ variable: RuntimeVariable<T>) -> T {
        getLogging()?.runtimeValue(variable) ?? variable.defaultValue
    }
}
