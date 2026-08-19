// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct AppDistributionInspector {
    private enum Constants {
        static let sandboxReceipt = "sandboxReceipt"
    }

    private let bundle: Bundle

    init(bundle: Bundle = .main) {
        self.bundle = bundle
    }

    /// This is a best effort. App Store and TestFlight builds get their provisioning profile
    /// stripped during processing, so we fall back to the receipt name to tell them apart. Not
    /// every iOS version hands us a receipt either, and when nothing works we return `.unknown`
    /// rather than guessing.
    ///
    /// - returns: the environment the app was built for, and the team identifier when we can get it.
    func inspect() -> AppDistributionInfo {
        if let profileInfo = ProvisioningProfileInspector(bundle: self.bundle).inspect() {
            return profileInfo
        }

        guard let receiptURL = self.bundle.appStoreReceiptURL else {
            return AppDistributionInfo(environment: .unknown, teamIdentifier: nil)
        }

        return AppDistributionInfo(
            environment: receiptURL.lastPathComponent == Constants.sandboxReceipt
                ? .testFlight
                : .production,
            teamIdentifier: nil
        )
    }
}
