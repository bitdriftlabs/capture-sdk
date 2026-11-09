// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// API endpoint. Acts as a central repository for all remote paths defined by the Swift native layer.
enum APIEndpoint {
    case reportError
    case getTemporaryDeviceCode

    var path: String {
        switch self {
        case .reportError:
            "/v1/sdk-errors"
        case .getTemporaryDeviceCode:
            "/v1/device/code"
        }
    }
}
