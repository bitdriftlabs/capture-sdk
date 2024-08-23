// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

private enum EncodableError: Error {
    case encodingFailure(Error)
    case stringInitializationFailure
}

extension ISO8601DateFormatter {
    // The default formatter used for encoding dates to strings. The SDK initializes and reuses
    // only one instance of this formatter, potentially accessed from within multiple
    // different threads/queues.
    //
    // SAFETY: Although the documentation of `ISO8601DateFormatter` doesn't mention thread safety,
    // its close companion `DateFormatter` is thread safe, and many libraries out there use
    // `ISO8601DateFormatter` as if it were thread safe.
    static var `default`: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions.update(with: .withFractionalSeconds)
        return formatter
    }()
}

extension Encodable {
    /// Encodes the given receiver to a String. The conversion handles strings, dates, and data instances
    /// in a special way and falls back to JSON encoding for all other types.
    /// The special handling for the aforementioned types is intended to keep the encoding cost low
    /// and make the output of the operation more human-readable by removing unnecessary
    /// surrounding quotes from the string representation of these types.
    ///
    /// - returns: String representation of the receiver.
    func encodeToString() throws -> String {
        if let value = self as? String {
            return value
        } else if let value = self as? Date {
            // Return "1970-01-01T00:00:00.000Z" as opposed to "\"1970-01-01T00:00:00.000Z\""
            return ISO8601DateFormatter.default.string(from: value)
        } else if let value = self as? Data {
            // Return "dGVzdA==" as opposed to "\"dGVzdA==\""
            return value.base64EncodedString()
        } else {
            let encoder = JSONEncoder.makeDefault()
            do {
                let value = try encoder.encode(self)
                return try value.encodeToString()
            } catch let error {
                throw EncodableError.encodingFailure(error)
            }
        }
    }
}

private extension Data {
    func encodeToString() throws -> String {
        guard let stringValue = String(data: self, encoding: .utf8) else {
            throw EncodableError.stringInitializationFailure
        }

        return stringValue
    }
}
