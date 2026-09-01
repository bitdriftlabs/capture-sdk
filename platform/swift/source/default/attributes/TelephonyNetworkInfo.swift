// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CapturePassable
import CoreTelephony
import Foundation

// Tracks attributes of the currently active cellular provider.
final class TelephonyNetworkInfo: NSObject {
    private let underlyingNetworkInfo = CTTelephonyNetworkInfo()
    private let stateQueue = DispatchQueue.serial(withLabelSuffix: "TelephonyNetworkInfo", target: .default)

    private var dataServiceIdentifier: String?
    private weak var logger: CoreLogging?
    private var radioType: String?

    override init() {
        // On iOS 13 and up, we initialize the initial value of `dataServiceIdentifier` and start tracking
        // updates to its value via `CTTelephonyNetworkInfo`'s delegate.
        // We use the information about the currently active `dataServiceIdentifier` to retrieve the
        // active radio type each time the active `dataServiceIdentifier` changes or relevant cellular
        // provider settings are updated.
        let dataServiceIdentifier = self.underlyingNetworkInfo.dataServiceIdentifier
        self.radioType = self.underlyingNetworkInfo.radioType(for: dataServiceIdentifier)
        self.dataServiceIdentifier = dataServiceIdentifier

        super.init()

        // Keep track of the active data service for cases when device uses multiple SIMs.
        // All delegate's callbacks are dispatched asynchronously to a global queue with `default` QoS.
        self.underlyingNetworkInfo.delegate = self
    }

    func start(with logger: CoreLogging) {
        self.stateQueue.sync {
            self.logger = logger
            self.publishRadioType()
        }
    }

    func initialOotbFields() -> [Field] {
        self.stateQueue.sync {
            [
                Field(key: "radio_type", data: (self.radioType ?? "unknown") as NSString, type: .string),
            ]
        }
    }

    // MARK: - Private

    private func updateDataServiceNetworkInfo() {
        let radioType = self.underlyingNetworkInfo.radioType(for: self.dataServiceIdentifier)
        guard self.radioType != radioType else {
            return
        }

        self.radioType = radioType
        self.publishRadioType()
    }

    private func publishRadioType() {
        self.logger?.updateOotbField(withKey: "radio_type", value: self.radioType ?? "unknown")
    }
}

extension TelephonyNetworkInfo: CTTelephonyNetworkInfoDelegate {
    public func dataServiceIdentifierDidChange(_ identifier: String) {
        self.stateQueue.async {
            self.dataServiceIdentifier = identifier
            self.updateDataServiceNetworkInfo()
        }
    }
}

private extension CTTelephonyNetworkInfo {
    func radioType(for identifier: String?) -> String? {
        return identifier.flatMap { self.serviceCurrentRadioAccessTechnology?[$0] }
    }
}
