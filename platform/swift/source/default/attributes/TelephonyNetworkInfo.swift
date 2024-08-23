// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import CoreTelephony
import Foundation

// Tracks attributes of the currently active cellular provider.
final class TelephonyNetworkInfo: NSObject {
    private let dataServiceIdentifier: Atomic<String?>
    private let underlyingNetworkInfo = CTTelephonyNetworkInfo()

    var logger: CoreLogging?

    let radioType: Atomic<String?>

    override init() {
        if #available(iOS 13.0, *) {
            // On iOS 13 and up, we initialize the initial value of `dataServiceIdentifier` and start tracking
            // updates to its value via `CTTelephonyNetworkInfo`'s delegate.
            // We use the information about the currently active `dataServiceIdentifier` to retrieve the
            // active radio type each time the active `dataServiceIdentifier` changes or relevant cellular
            // provider settings are updated.
            let dataServiceIdentifier = self.underlyingNetworkInfo.dataServiceIdentifier
            self.radioType = Atomic(self.underlyingNetworkInfo.radioType(for: dataServiceIdentifier))
            self.dataServiceIdentifier = Atomic(dataServiceIdentifier)
        } else {
            // On iOS 12, we initialize the active radio type with the value of `CTTelephonyNetworkInfo`'s
            // `currentRadioAccessTechnology` property and update it each time the settings of any of the
            // available cellular providers are updated.
            let underlyingNetworkInfo =
                self.underlyingNetworkInfo as IgnoringCurrentRadioAccessTechnologyDeprecation
            self.radioType = Atomic(underlyingNetworkInfo.currentRadioAccessTechnology)
            self.dataServiceIdentifier = Atomic(nil)
        }

        super.init()

        // Keep track of the active data service for cases when device uses multiple SIMs.
        // All delegate's callbacks are dispatched asynchronously to a global queue with `default` QoS.
        if #available(iOS 13.0, *) {
            // On iOS 12 and earlier we will not be able to learn about when the active SIM card changes.
            self.underlyingNetworkInfo.delegate = self
            self.updateDataServiceNetworkInfo()
        }

        // This callback is dispatched asynchronously to a global queue with `default` QoS.
        self.underlyingNetworkInfo
            .serviceSubscriberCellularProvidersDidUpdateNotifier = { [weak self] identifier in
                guard let self else {
                    return
                }

                if #available(iOS 13.0, *) {
                    // We update network info only if cellular provider's identifier matches the
                    // identifier of the currently active data service provider. Otherwise, the update
                    // is for a SIM (cellular provider) that's not actively used for data transfer
                    // and we ignore it.
                    if identifier == self.dataServiceIdentifier.load() {
                        self.updateDataServiceNetworkInfo()
                    }
                } else {
                    self.radioType.update { value in
                        let networkInfo =
                            self.underlyingNetworkInfo as IgnoringCurrentRadioAccessTechnologyDeprecation
                        value = networkInfo.currentRadioAccessTechnology
                    }
                }
            }
    }

    // MARK: - Private

    @available(iOS 13.0, *)
    private func updateDataServiceNetworkInfo() {
        let identifier = self.dataServiceIdentifier.load()

        let radioType = self.underlyingNetworkInfo.radioType(for: identifier)
        self.radioType.update { $0 = radioType }
    }
}

@available(iOS 13.0, *)
extension TelephonyNetworkInfo: CTTelephonyNetworkInfoDelegate {
    public func dataServiceIdentifierDidChange(_ identifier: String) {
        self.dataServiceIdentifier.update { $0 = identifier }
        self.updateDataServiceNetworkInfo()
    }
}

private extension CTTelephonyNetworkInfo {
    func radioType(for identifier: String?) -> String? {
        return identifier.flatMap { self.serviceCurrentRadioAccessTechnology?[$0] }
    }
}

// A workaround to get rid of Xcode deprecation warnings for accessing `currentRadioAccessTechnology` property
// that was deprecated in iOS 12. Refer to
// https://nshipster.com/available/#working-around-deprecation-warnings for more details.
protocol IgnoringCurrentRadioAccessTechnologyDeprecation {
    @available(iOS, deprecated: 13, message: "Use on iOS 12 only")
    var currentRadioAccessTechnology: String? { get }
}

extension CTTelephonyNetworkInfo: IgnoringCurrentRadioAccessTechnologyDeprecation {}
