// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

internal import CapturePassable
import Foundation

/// Wraps a custom timestamp provider for the Objective-C bridge.
final class TimestampProviderController {
    private let dateProvider: DateProvider

    init(dateProvider: DateProvider) {
        self.dateProvider = dateProvider
    }
}

extension TimestampProviderController: CapturePassable.TimestampProvider {
    func timestamp() -> TimeInterval {
        self.dateProvider.getDate().timeIntervalSince1970
    }
}
