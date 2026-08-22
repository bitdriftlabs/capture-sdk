// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture

@available(
*,
deprecated,
message: "Use Logger.start(initialFields:) to seed fields at startup and Logger.addField(withKey:value:) to update them."
)
public final class MockFieldProvider: FieldProvider {
    public let getFieldsClosure: () -> Fields

    public init(getFieldsClosure: @escaping () -> Fields = { [:] }) {
        self.getFieldsClosure = getFieldsClosure
    }

    public func getFields() -> Fields {
        return self.getFieldsClosure()
    }
}
