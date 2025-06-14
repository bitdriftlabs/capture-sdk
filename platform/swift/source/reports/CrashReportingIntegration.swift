// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

extension Integration {
    /// Enables reporting unexpected app terminations
    ///
    /// - parameter type: mechanism used for crash detection
    ///
    /// - returns: The crash reporting integration
    public static func crashReporting(_ type: IssueReporterType = .builtIn) -> Integration {
        .init { _, _ in
            Logger.initFatalIssueReporting(type)
        }
    }
}
