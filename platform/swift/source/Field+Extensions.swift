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
        if let value = value as? SessionReplayCapture {
            return Field(key: key, data: value.data as NSData, type: .data)
        } else if let mapValue = value as? [String: any FieldValue] {
            let nsDict = try Self.convertMapToNSDictionary(mapValue)
            return Field(key: key, data: nsDict, type: .map)
        } else {
            let stringValue = try value.encodeToString()
            return Field(key: key, data: stringValue as NSString, type: .string)
        }
    }

    /// Converts a map field value into a Field with type `.map`.
    ///
    /// - parameter key:   The field key.
    /// - parameter value: The map value with string keys and FieldValue values.
    ///
    /// - returns: The created `Field` instance.
    static func make(key: String, value: [String: any FieldValue]) throws -> Field {
        let nsDict = try Self.convertMapToNSDictionary(value)
        return Field(key: key, data: nsDict, type: .map)
    }

    private static func convertMapToNSDictionary(_ map: [String: any FieldValue]) throws -> NSDictionary {
        let nsDict = NSMutableDictionary()
        for (k, v) in map {
            nsDict[k] = try convertFieldValueToNSObject(v)
        }
        return nsDict
    }

    private static func convertFieldValueToNSObject(_ value: any FieldValue) throws -> AnyObject {
        if let nested = value as? [String: any FieldValue] {
            return try convertMapToNSDictionary(nested)
        } else if let nsDict = value as? NSDictionary {
            return nsDict
        } else if let stringVal = value as? String {
            return stringVal as NSString
        } else if let intVal = value as? Int {
            return intVal as NSNumber
        } else if let int64Val = value as? Int64 {
            return int64Val as NSNumber
        } else if let uintVal = value as? UInt {
            return uintVal as NSNumber
        } else if let uint64Val = value as? UInt64 {
            return uint64Val as NSNumber
        } else if let doubleVal = value as? Double {
            return doubleVal as NSNumber
        } else if let floatVal = value as? Float {
            return floatVal as NSNumber
        } else if let boolVal = value as? Bool {
            return boolVal as NSNumber
        } else if let dataVal = value as? Data {
            return dataVal as NSData
        } else {
            let stringValue = try value.encodeToString()
            return stringValue as NSString
        }
    }
}
