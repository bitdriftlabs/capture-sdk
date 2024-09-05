// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

// A convenient wrapper for determining whether a given response from the server should be
// considered a success or not.
struct HTTPStatus {
    private let statusCode: Int

    let serverStatusCode: Int?
    let clientErrorCode: Int?

    private init(statusCode: Int, serverStatusCode: Int?, clientErrorCode: Int?) {
        self.statusCode = statusCode
        self.serverStatusCode = serverStatusCode
        self.clientErrorCode = clientErrorCode
    }

    init(response: URLResponse?, error: Error?) {
        let serverStatusCode = (response as? HTTPURLResponse)?.statusCode
        let clientErrorCode = (error as NSError?).map(\.code)

        let statusCode: Int?
        if serverStatusCode?.isSuccess != true {
            /// If status from the server doesn't represent success prioritize it
            /// over client-side errors.
            statusCode = serverStatusCode ?? clientErrorCode
        } else {
            /// Otherwise, prioritize client-side statuses. An example for when this
            /// codepath is executed is when client receives a success status code
            /// from the server which is followed by client-side cancellation of a request
            /// (represented as error with code -999) before the client receives body
            /// from the server.
            statusCode = clientErrorCode ?? serverStatusCode
        }

        self = HTTPStatus(
            statusCode: statusCode ?? -1,
            serverStatusCode: serverStatusCode,
            clientErrorCode: clientErrorCode
        )
    }

    /// Indicates whether the request completed successfully or not. Note that a request may complete with a
    /// success-like HTTP status (e.g., 200) but still be considered a failure if the client receives a 200
    /// response status code from the server but fails to fetch the entire response payload (e.g., due to
    /// request cancellation).
    var isSuccess: Bool {
        self.statusCode.isSuccess
    }
}

private extension Int {
    var isSuccess: Bool {
        return (200..<400).contains(self)
    }
}
