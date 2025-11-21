// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Specifies the report processing type
@objc
public enum ReportProcessingSession: Int32 {
    /// For issue reports on ongoing session
    case current = 0

    /// For issue reports stored on previous session
    case previousRun = 1
}
