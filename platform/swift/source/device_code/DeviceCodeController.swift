// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Responsible for fetching temporary device tokens from bitdrift remote services.
final class DeviceCodeController {
    private let client: APIClient

    /// Creates a new controller for a given device ID.
    ///
    /// - parameter client: API client to use when talking with bitdrift API.
    init(client: APIClient) {
        self.client = client
    }

    func createTemporaryDeviceCode(deviceID: String, completion: @escaping (Result<String, Error>) -> Void) {
        self.client.perform(
            endpoint: .getTemporaryDeviceCode,
            request: DeviceCodeRequest(deviceID: deviceID)
        ) { (result: Result<DeviceCodeResponse, Error>) in
            completion(result.map(\.code))
        }
    }
}

private struct DeviceCodeRequest: Encodable {
    let deviceID: String

    enum CodingKeys: String, CodingKey {
        case deviceID = "device_id"
    }
}

private struct DeviceCodeResponse: Decodable {
    let code: String
}
