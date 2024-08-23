// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

// The header which can be used used by upstream dependencies to determine whether a specific URL request
// should be instrumented or not. Specifically, it's used by `CaptureExtensions` integration library to ensure
// that we do not emit logs for Capture internal network requests.
// The possible values of the header are `false`/`true`. The lack of the value means that the request is not
// a Capture internal request.
private let kHeaderCaptureAPI = "x-capture-api"

extension URLRequest {
    /// Sets headers for internal Capture requests.
    mutating func setInternalHeaders() {
        self.addValue("true", forHTTPHeaderField: kHeaderCaptureAPI)
    }

    func isCaptureAPI() -> Bool {
        self.allHTTPHeaderFields?[kHeaderCaptureAPI] == "true"
    }
}
