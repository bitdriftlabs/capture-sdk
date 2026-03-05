// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Metadata about a detected fatal issue.
@objc(CAPIssueReport)
public final class IssueReport: NSObject {
    /// The type of issue (for example, "ANR", "Native Crash", "Crash").
    @objc public let reportType: String

    /// The primary error identifier (for example, exception class name or signal name).
    @objc public let reason: String

    /// Additional details about the issue (for example, error message).
    @objc public let details: String

    /// The bitdrift session ID of the session in which the issue occurred.
    @objc public let sessionID: String

    /// Additional crash fields associated with this issue report.
    @objc public let fields: [String: String]

    @objc
    public init(
        reportType: String,
        reason: String,
        details: String,
        sessionID: String,
        fields: [String: String]
    ) {
        self.reportType = reportType
        self.reason = reason
        self.details = details
        self.sessionID = sessionID
        self.fields = fields
    }
}

/// Callback invoked before an issue report (crash, ANR, etc.) is sent.
@objc
public protocol IssueReportCallback: AnyObject {
    /// Called before an issue report is sent.
    ///
    /// - parameter report: The issue report metadata.
    func onBeforeReportSend(report: IssueReport)
}

/// Configuration for issue report callbacks.
///
/// Use this to provide:
/// - the queue where callbacks run
/// - the callback that receives issue report metadata before send
@objc(CAPIssueCallbackConfiguration)
public final class IssueCallbackConfiguration: NSObject {
    private let callbackQueue: DispatchQueue
    private let issueReportCallback: IssueReportCallback

    /// Creates issue callback configuration.
    ///
    /// - parameter callbackQueue:       Queue used to run callback invocations.
    /// - parameter issueReportCallback: Callback invoked before an issue report is sent.
    @objc
    public init(callbackQueue: DispatchQueue, issueReportCallback: IssueReportCallback) {
        self.callbackQueue = callbackQueue
        self.issueReportCallback = issueReportCallback
    }

    // Called from Rust/FFI via Objective-C selector lookup. Keep selector stable.
    @objc(dispatch:)
    internal func dispatch(_ report: IssueReport) {
        callbackQueue.async { [issueReportCallback] in
            issueReportCallback.onBeforeReportSend(report: report)
        }
    }
}
