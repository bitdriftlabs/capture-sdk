// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import CapturePassable
import Foundation

/// A log field that's a part of the log that's being uploaded.
public struct UploadedField: Equatable {
    public let key: String
    public let value: AnyObject
    public let type: FieldType

    public static func == (lhs: UploadedField, rhs: UploadedField) -> Bool {
        return lhs.key == rhs.key && (lhs.value as? NSObject)?.isEqual(rhs.value as? NSObject) == true
    }

    public init(key: String, value: AnyObject, type: FieldType) {
        self.key = key
        self.value = value
        self.type = type
    }
}

/// Represents a single log that has been uploaded to the test API server.
/// This is created on the Swift side but populated by the Rust side, which
/// works around some trickiness around initializing the object on the Rust side.
@objc
public class UploadedLog: NSObject {
    @objc public var logType: UInt32 = 0
    @objc public var logLevel: UInt32 = 0
    @objc public var message: String = ""
    @objc public var sessionID: String = ""

    public var fields: [UploadedField] = []

    /// Adds a string field to the uploaded log.
    /// This allows Rust to not be concerned with creating a `Field` object.
    ///
    /// - parameter key:   The field's key.
    /// - parameter value: The field's value.
    @objc
    public func addStringField(key: String, value: String) {
        self.fields.append(UploadedField(key: key, value: value as NSString, type: .string))
    }

    /// Adds a binary field to the uploaded log.
    /// This allows Rust to not be concerned with creating a `Field` object.
    ///
    /// - parameter key:   The field's key.
    /// - parameter value: The field's value.
    @objc
    public func addBinaryField(key: String, value: Data) {
        self.fields.append(UploadedField(key: key, value: value as NSData, type: .data))
    }
}
