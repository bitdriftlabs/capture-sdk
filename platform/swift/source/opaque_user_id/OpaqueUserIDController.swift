// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

/// Responsible for registering opaque user identifiers with bitdrift remote services.
final class OpaqueUserIDController {
    private let client: APIClient
    private let timeProvider: TimeProvider
    private let lock = Lock()

    private let maxCallsPerMinute = 5
    private let intervalSeconds: TimeInterval = 60
    private var callTimestamps = [Uptime]()

    init(client: APIClient, timeProvider: TimeProvider = SystemTimeProvider()) {
        self.client = client
        self.timeProvider = timeProvider
    }

    func registerOpaqueUserID(_ opaqueUserID: String, deviceID: String) {
        guard self.shouldSend() else {
            return
        }

        self.client.perform(
            endpoint: .registerOpaqueUserID,
            request: OpaqueUserIDRequest(opaqueUserID: opaqueUserID, deviceID: deviceID)
        ) { result in
            switch result {
            case .success:
                print("[Capture] Successfully registered opaque user ID")
            case .failure(let error):
                print("[Capture] Failed to register opaque user ID: \(error)")
            }
        }
    }

    private func shouldSend() -> Bool {
        self.lock.withLock {
            let now = self.timeProvider.uptime()
            self.callTimestamps.removeAll { timestamp in
                self.timeProvider.timeIntervalSince(timestamp) >= self.intervalSeconds
            }

            guard self.callTimestamps.count < self.maxCallsPerMinute else {
                return false
            }

            self.callTimestamps.append(now)
            return true
        }
    }
}

private struct OpaqueUserIDRequest: Encodable {
    let opaqueUserID: String
    let deviceID: String

    enum CodingKeys: String, CodingKey {
        case opaqueUserID = "opaque_user_id"
        case deviceID = "device_id"
    }
}
