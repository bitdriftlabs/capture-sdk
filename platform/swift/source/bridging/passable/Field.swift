// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

import Foundation

@objc
public enum FieldType: Int {
    case string = 0
    case data
}

@objc
public class Field: NSObject {
    @objc public let key: String
    @objc public let data: AnyObject
    @objc public let type: FieldType

    public init(key: String, data: AnyObject, type: FieldType) {
        self.key = key
        self.data = data
        self.type = type

        super.init()
    }

    override public func isEqual(_ object: Any?) -> Bool {
        guard let object = object as? Field else {
            return false
        }

        return self.key == object.key
            && self.data.isEqual(object.data)
            && self.type == object.type
    }
}
