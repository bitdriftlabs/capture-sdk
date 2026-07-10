// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

typealias WebViewSerializableFields = [String: WebViewSerializableValue]

enum WebViewSerializableValue: Codable, Equatable {
    case array([WebViewSerializableValue])
    case bool(Bool)
    case null
    case number(Double)
    case object(WebViewSerializableFields)
    case string(String)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if let object = try? container.decode(WebViewSerializableFields.self) {
            self = .object(object)
        } else if let array = try? container.decode([WebViewSerializableValue].self) {
            self = .array(array)
        } else if let bool = try? container.decode(Bool.self) {
            self = .bool(bool)
        } else if let integer = try? container.decode(Int.self) {
            self = .number(Double(integer))
        } else if let double = try? container.decode(Double.self) {
            self = .number(double)
        } else if let string = try? container.decode(String.self) {
            self = .string(string)
        } else if container.decodeNil() {
            self = .null
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unsupported serializable value"
            )
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()

        switch self {
        case .array(let value):
            try container.encode(value)
        case .bool(let value):
            try container.encode(value)
        case .null:
            try container.encodeNil()
        case .number(let value):
            try container.encode(value)
        case .object(let value):
            try container.encode(value)
        case .string(let value):
            try container.encode(value)
        }
    }
    
    var fieldStringValue: String {
        switch self {
        case .array, .object:
            return (try? encodeToString()) ?? String(describing: self)
        case .bool(let value):
            return String(value)
        case .null:
            return "null"
        case .number(let value):
            return String(value)
        case .string(let value):
            return value
        }
    }
}
