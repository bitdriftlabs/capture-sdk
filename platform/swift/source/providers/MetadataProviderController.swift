// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CapturePassable
import Foundation

/// To support the date and session provider being able to change at any time, we need to be able
/// to wrap up the Atomics in an @objc protocol
final class MetadataProviderController {
    typealias ErrorReporter = (_ context: String, _ error: Error) -> Void
    typealias FieldGetter = () -> Fields

    var errorHandler: ErrorReporter = { _, _ in assertionFailure("errorHandler not set") }

    let dateProvider: DateProvider

    let ootbFieldGetters: [FieldGetter]
    let customFieldGetters: [FieldGetter]

    init(
        dateProvider: DateProvider,
        ootbFieldGetters: [FieldGetter],
        customFieldGetters: [FieldGetter]
    ) {
        self.dateProvider = dateProvider
        self.ootbFieldGetters = ootbFieldGetters
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

extension MetadataProviderController: CapturePassable.MetadataProvider {
    func timestamp() -> TimeInterval {
        self.dateProvider.getDate().timeIntervalSince1970
    }

    func ootbFields() -> [Field] {
        self.getFields(fieldGetters: self.ootbFieldGetters)
    }

    func customFields() -> [Field] {
        self.getFields(fieldGetters: self.customFieldGetters)
    }
}
