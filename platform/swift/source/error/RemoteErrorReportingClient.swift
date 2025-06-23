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
    // swiftlint:disable line_length
    /// Although the interface suggest that we are dealing with a list of field providers in here, in practice it's only
    /// `ClientAttributes` fields provider that's passed in here. That's due to the fact that the api accepts only a
    /// few client attributes, so sending any other fields is pointless.
    private let fieldProviders: [FieldProvider]

    init(client: APIClient, fieldProviders: [FieldProvider]) {
        self.client = client
        self.fieldProviders = fieldProviders
    }

    // MARK: - Private

    private func sendErrorRequest(with message: String, fields: [String: String]) {
        let extraFields = self.fieldProviders
            .flatMap { fieldProvider in
                fieldProvider.getFields().compactMap { key, value -> (String, String)? in
                    // Ignore encoding errors. We cannot do much if encoding to String fails
                    // in here as we are already on an error reporting path
                    if let stringValue = try? value.encodeToString() {
                        return (key, stringValue)
                    } else {
                        return nil
                    }
                }
            }
            .compactMap { key, value in
                ("x-" + key.replacingOccurrences(of: "_", with: "-"), value)
            }

        self.client.perform(
            endpoint: .reportError,
            request: ReportErrorRequest(message: message),
            headers: fields.merging(extraFields) { current, _ in current }
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
