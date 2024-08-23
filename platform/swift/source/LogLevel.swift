// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Levels associated with a log message.
@objc
public enum LogLevel: Int32, CaseIterable {
    /// ERROR mode is for when the app is in distress, and needs to be fixed as soon as possible,
    case error = 4

    /// WARNING mode is for when an unexpected technical or business event happened, but probably no immediate
    /// human intervention is required. This is not necessarily an error but developers will want to review
    /// these issues as soon as possible to understand the impact.
    case warning = 3

    /// INFO mode is for things we want to see at high volume in case we need to trace back an issue.
    /// "Session" lifecycle events (login, logout, etc.) go here. Typical business exceptions can go here
    /// (e.g. login failed due to bad credentials).
    case info = 2

    /// DEBUG mode is for any message that is helpful in tracking the flow through the system and isolating
    /// issues.
    case debug = 1

    /// TRACE mode is for extremely detailed and potentially high volume logs that you don't typically want
    /// enabled even during normal development. Examples include network requests and responses.
    case trace = 0
}
