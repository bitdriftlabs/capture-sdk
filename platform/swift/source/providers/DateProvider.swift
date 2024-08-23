// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// This interface provides an accessor as an alternative to get the system time (default). Example use cases:
/// - Providing a time backed up by NTP that is agnostic to local modifications.
/// - Provide a mocked time for unit tests.
public protocol DateProvider {
    /// Captures current time.
    ///
    /// - returns: Current time.
    func getDate() -> Date
}
