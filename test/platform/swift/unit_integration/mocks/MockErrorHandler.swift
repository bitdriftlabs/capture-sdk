// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture

final class MockErrorHandler {
    struct ErrorReport {
        let context: String
        let error: Error
    }

    private(set) var errors: [ErrorReport] = []

    func handleError(context: String, error: Error) {
        self.errors.append(ErrorReport(context: context, error: error))
    }
}
