// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CapturePassable
import Foundation

/// Wraps custom field getters for the Objective-C bridge.
final class CustomFieldsProviderController {
    typealias ErrorReporter = (_ context: String, _ error: Error) -> Void
    typealias FieldGetter = () -> Fields

    var errorHandler: ErrorReporter = { _, _ in assertionFailure("errorHandler not set") }

    let customFieldGetters: [FieldGetter]

    init(customFieldGetters: [FieldGetter]) {
        self.customFieldGetters = customFieldGetters
    }

    private func getFields(fieldGetters: [FieldGetter]) -> [CapturePassable.Field] {
        // The order in which we process field providers of a given kind matters.
        // The earlier in the array a given field lands the highest its priority is.
        return fieldGetters
            .flatMap { $0() }
            .compactMap { [weak self] keyValue in
                do {
                    return try Field.make(keyValue: keyValue)
                } catch let error {
                    self?.errorHandler("metadata provider, get fields", error)
                    return nil
                }
            }
    }
}

extension CustomFieldsProviderController: CapturePassable.CustomFieldsProvider {
    func customFields() -> [Field] {
        return self.getFields(fieldGetters: self.customFieldGetters)
    }
}
