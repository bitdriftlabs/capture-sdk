// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// A configuration representing the feature set enabled for Capture.
public struct Configuration {
    /// The session replay configuration. Pass `nil` to disable session replay.
    public var sessionReplayConfiguration: SessionReplayConfiguration?

    /// .enabled if Capture should initialize in minimal activity mode
    public var sleepMode: SleepMode

    /// true if Capture should enable Fatal Issue Reporting
    public var enableFatalIssueReporting: Bool

    /// If specified, this path will be used to store all SDK internal files instead of the default location (i.e. The app's document directory).
    public var rootFileURL: URL?

    /// The base URL of Capture API. Depend on its default value unless specifically instructed otherwise during discussions with
    /// bitdrift. Defaults to bitdrift's hosted API base URL.
    let apiURL: URL

    /// Initializes a new instance of the Capture configuration.
    ///
    /// - parameter sessionReplayConfiguration: The session replay configuration to use. Passing `nil` disables the feature.
    /// - parameter sleepMode:                  .enabled if Capture should initialize in minimal activity mode
    /// - parameter enableFatalIssueReporting:  true if Capture should enable Fatal Issue Reporting
    /// - parameter apiURL:                     The base URL of Capture API. Depend on its default value unless
    ///                                         specifically instructed otherwise during discussions with bitdrift. Defaults
    ///                                         to bitdrift's SaaS API base URL.
    /// - parameter rootFileURL:                If specified, this path will be used to store all SDK internal files instead of
    ///                                         the default location (i.e. The app's document directory).
    public init(
        sessionReplayConfiguration: SessionReplayConfiguration? = .init(),
        sleepMode: SleepMode = .disabled,
        enableFatalIssueReporting: Bool = true,
        // swiftlint:disable:next force_unwrapping use_static_string_url_init
        apiURL: URL = URL(string: "https://api.bitdrift.io")!,
        rootFileURL: URL? = nil
    ) {
        self.sessionReplayConfiguration = sessionReplayConfiguration
        self.sleepMode = sleepMode
        self.enableFatalIssueReporting = enableFatalIssueReporting
        self.apiURL = apiURL
        self.rootFileURL = rootFileURL
    }
}
