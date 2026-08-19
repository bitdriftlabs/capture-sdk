// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Deprecated compatibility type for the former session-strategy bridge.
///
/// Use `SessionConfiguration` instead. Capture no longer reads `generateSessionID()`; SDK-created
/// session IDs are UUIDs.
@available(*, deprecated, message: "Use SessionConfiguration instead.")
@objc
public enum SessionStrategyType: Int {
    case fixed = 0
    case activityBased
}

/// Deprecated compatibility protocol for the former session-strategy bridge.
///
/// Use `SessionConfiguration` instead. Capture no longer reads `generateSessionID()`; it remains
/// declared only so existing clients continue to compile and link.
@available(*, deprecated, message: "Use SessionConfiguration instead.")
@objc
public protocol SessionStrategyProvider {
    /// The former session strategy type.
    ///
    /// - returns: The former session strategy type.
    func sessionStrategyType() -> SessionStrategyType

    /// The former inactivity threshold, in minutes.
    ///
    /// - returns: The former inactivity threshold in minutes.
    func inactivityThresholdMins() -> Int

    /// Compatibility-only callback. Capture no longer invokes this method.
    ///
    /// - returns: A legacy session ID.
    func generateSessionID() -> String

    /// Compatibility callback for session-ID changes.
    ///
    /// - parameter sessionID: The updated session ID.
    func sessionIDChanged(_ sessionID: String)
}
