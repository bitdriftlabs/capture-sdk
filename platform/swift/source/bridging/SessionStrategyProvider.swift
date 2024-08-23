// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

@objc
public enum SessionStrategyType: Int {
    case fixed = 0
    case activityBased
}

@objc
public protocol SessionStrategyProvider {
    /// The session strategy type.
    ///
    /// - returns: The session strategy type.
    func sessionStrategyType() -> SessionStrategyType

    /// The inactivity threshold after which `activityBased` session strategy generates
    /// a new session ID.
    ///
    /// - returns: The inactivity threshold in minutes.
    func inactivityThresholdMins() -> Int

    /// A `fixed` session strategy callback to use for generating new session identifiers.
    ///
    /// - returns: The generated session ID.
    func generateSessionID() -> String

    /// A callback called by `activityBased` session strategy each time inactivity
    /// threshold is exceeded and new session ID is generated.
    ///
    /// - parameter sessionID: The new session ID.
    func sessionIDChanged(_ sessionID: String)
}
