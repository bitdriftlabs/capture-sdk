// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Describes the strategy to use for session management.
public enum SessionStrategy {
    case configuration(SessionConfiguration)
    /// Deprecated compatibility shim for a session that does not expire during a process but is
    /// replaced when the SDK starts in a new process.
    ///
    /// Capture generates UUIDs for SDK-created sessions; use `SessionConfiguration.initialSessionID`
    /// when an application needs to supply an initial ID.
    case fixed

    /// Creates the deprecated fixed-session compatibility shim.
    ///
    /// - returns: The fixed-session compatibility shim.
    public static func fixed() -> Self {
        .fixed
    }

    /// A session strategy that generates a new session ID after a certain period of app inactivity.
    ///
    /// The inactivity duration is measured by the minutes elapsed since the last log. The session ID is
    /// persisted to disk and survives app restarts.
    ///
    /// For this session strategy, each log emitted by the SDK — including those from session replay and
    /// resource monitoring feature — is considered an app activity.
    ///
    /// - parameter inactivityThresholdMins: The amount of minutes of inactivity after which a session ID
    ///                                      changes. The default value is 30 minutes.
    /// - parameter onSessionIDChanged:      Closure that receives the active session ID after each session
    ///                                      start or rotation, including explicit starts with the current ID.
    ///                                      This callback is dispatched asynchronously to the main queue.
    ///                                      Calls from overlapping session starts are not guaranteed to
    ///                                      arrive in transition order; use `Logger.sessionID` for the
    ///                                      current session ID.
    case activityBased(inactivityThresholdMins: Int = 30, onSessionIDChanged: ((String) -> Void)? = nil)
}

extension SessionStrategy {
    func makeSessionConfiguration() -> SessionConfiguration {
        switch self {
        case let .configuration(configuration):
            configuration
        case .fixed:
            SessionConfiguration()
        case let .activityBased(inactivityThresholdMins, onSessionIDChanged):
            SessionConfiguration(
                inactivityTimeout: TimeInterval(inactivityThresholdMins) * 60,
                onSessionIDChanged: onSessionIDChanged
            )
        }
    }
}
