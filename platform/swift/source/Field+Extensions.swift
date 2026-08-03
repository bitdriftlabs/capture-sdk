// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
internal import CapturePassable
import Foundation

extension Field {
    /// Converts a given key-value pair into a Field. Objects and arrays are represented as structured values.
    ///
    /// The method throws an error if the encoding fails.
    ///
    /// - parameter keyValue: The key-value pair to create the field from.
    ///
    /// - returns: The created `Field` instance.
    static func make(keyValue: (key: String, value: FieldValue)) throws -> Field {
        try self.make(key: keyValue.key, value: keyValue.value)
    }

    /// Converts a given key-value pair into a Field. Objects and arrays are represented as structured values.
    ///
    /// The method throws an error if the encoding fails.
    ///
    /// - parameter key:   The field key.
    /// - parameter value: The field value.
    ///
    /// - returns: The created `Field` instance .
    static func make(key: String, value: FieldValue) throws -> Field {
        try self.makeWithLegacyMatchingField(key: key, value: value).field
    }

    static func makeWithLegacyMatchingField(
        key: String,
        value: FieldValue
    ) throws -> (field: Field, legacyMatchingField: Field?) {
        if let value = value as? SessionReplayCapture {
            return (Field(key: key, data: value.data as NSData, type: .data), nil)
        }

        let encodedData = try JSONEncoder.makeDefault().encode(value)
        let object = try JSONSerialization.jsonObject(with: encodedData)
        if object is NSDictionary || object is NSArray {
            // Legacy workflows match this value without persisting a duplicate field.
            return (
                Field(key: key, data: object as AnyObject, type: .map),
                Field(key: key, data: String(decoding: encodedData, as: UTF8.self) as NSString, type: .string)
            )
        }

        return (Field(key: key, data: try value.encodeToString() as NSString, type: .string), nil)
    }
}
