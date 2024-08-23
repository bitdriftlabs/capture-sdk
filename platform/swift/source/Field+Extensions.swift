// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge
@_implementationOnly import CapturePassable
import Foundation

extension Field {
    /// Converts a given key-value pair into a Field. With the exception of screen replay captures,
    /// which are converted to Data values, all other types are represented as Strings.
    ///
    /// The method throws an error if the encoding fails.
    ///
    /// - parameter keyValue: The key-value pair to create the field from.
    ///
    /// - returns: The created `Field` instance.
    static func make(keyValue: (key: String, value: FieldValue)) throws -> Field {
        try self.make(key: keyValue.key, value: keyValue.value)
    }

    /// Converts a given key-value pair into a Field. With the exception of screen replay captures,
    /// which are converted to Data values, all other types are represented as Strings.
    ///
    /// The method throws an error if the encoding fails.
    ///
    /// - parameter key:   The field key.
    /// - parameter value: The field value.
    ///
    /// - returns: The created `Field` instance .
    static func make(key: String, value: FieldValue) throws -> Field {
        if let value = value as? SessionReplayScreenCapture {
            return Field(key: key, data: value.data as NSData, type: .data)
        } else {
            let stringValue = try value.encodeToString()
            return Field(key: key, data: stringValue as NSString, type: .string)
        }
    }
}
