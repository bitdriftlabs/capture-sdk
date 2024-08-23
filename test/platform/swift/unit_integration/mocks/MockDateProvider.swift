// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import Foundation

final class MockDateProvider: DateProvider {
    var getDateClosure: () -> Date

    // This should return "2022-10-26T17:56:41.520058155Z" when formatted.
    init(getDateClosure: @escaping () -> Date = { Date(timeIntervalSince1970: 1_666_807_001.52005815) }) {
        self.getDateClosure = getDateClosure
    }

    func getDate() -> Date {
        return self.getDateClosure()
    }
}
