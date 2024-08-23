// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
@testable import CaptureLoggerBridge
import Foundation
import XCTest

private struct FailingEncodable: Encodable {
    struct Error: Swift.Error {}

    func encode(to _: Encoder) throws {
        throw Error()
    }
}

final class MetadataProviderTests: XCTestCase {
    func testReportsFieldsErrors() throws {
        let errorHandler = MockErrorHandler()

        let provider = MetadataProvider(
            dateProvider: MockDateProvider(),
            ootbFieldProviders: [
                MockFieldProvider {
                    [
                        "foo": FailingEncodable(),
                        "bar": "bar_value",
                        "car": Data(),
                    ]
                },
            ],
            customFieldProviders: []
        )

        provider.errorHandler = errorHandler.handleError

        let fields = provider.ootbFields()
        XCTAssertEqual(fields.sorted(by: { $0.key < $1.key }), [
            try Field.make(key: "bar", value: "bar_value"),
            try Field.make(key: "car", value: Data()),
        ])
        XCTAssertEqual(errorHandler.errors.count, 1)
        XCTAssertEqual(errorHandler.errors[0].context, "metadata provider, get fields")
    }

    func testReportsCustomFieldsErrors() {
        let errorHandler = MockErrorHandler()

        let provider = MetadataProvider(
            dateProvider: MockDateProvider(),
            ootbFieldProviders: [],
            customFieldProviders: [
                MockFieldProvider {
                    [
                        "foo": FailingEncodable(),
                        "bar": "bar_value",
                        "car": Data(),
                    ]
                },
            ]
        )

        provider.errorHandler = errorHandler.handleError

        let fields = provider.customFields()
        XCTAssertEqual(fields.sorted(by: { $0.key < $1.key }), [
            try Field.make(key: "bar", value: "bar_value"),
            try Field.make(key: "car", value: Data()),
        ])
        XCTAssertEqual(errorHandler.errors.count, 1)
        XCTAssertEqual(errorHandler.errors[0].context, "metadata provider, get fields")
    }
}
