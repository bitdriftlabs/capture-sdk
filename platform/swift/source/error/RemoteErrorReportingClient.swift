// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

final class RemoteErrorReportingClient: NSObject {
    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    // MARK: - Private

    private func sendErrorRequest(with message: String, fields: [String: String]) {
        self.client.perform(
            endpoint: .reportError,
            request: ReportErrorRequest(message: message),
            headers: fields
        ) { _ in
            // Fire and forget.
        }
    }
}

// MARK: - ErrorReporting

extension RemoteErrorReportingClient: CaptureLoggerBridge.RemoteErrorReporting {
    func reportError(
        _ messageBufferPointer: UnsafePointer<UInt8>,
        fields: [String: String]
    ) {
        let message = String(cString: messageBufferPointer)
        self.sendErrorRequest(with: message, fields: fields)
    }
}

struct ReportErrorRequest: Encodable {
    let message: String
}
