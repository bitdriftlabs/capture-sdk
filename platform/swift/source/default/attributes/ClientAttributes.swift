// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import UIKit

/// Client attributes related to the app metadata and OS.
final class ClientAttributes {
    /// The app ID.
    private(set) var appID = Bundle.main.bundleIdentifier ?? "unknown"
    /// The app version. Up to three integers split using "." character.
    private(set) var appVersion: String =
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?.?.?"
    /// The build number identifier.
    private(set) var buildNumber: String =
        Bundle.main.infoDictionary?[kCFBundleVersionKey as String] as? String ?? "?"

    private let osVersion = UIDevice.current.systemVersion
}
