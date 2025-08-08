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
    private(set) var appID: String
    /// The app version. Up to three integers split using "." character.
    private(set) var appVersion: String =
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?.?.?"
    /// The build number identifier.
    private(set) var buildNumber: String =
        Bundle.main.infoDictionary?[kCFBundleVersionKey as String] as? String ?? "?"

    private let osVersion = UIDevice.current.systemVersion

    /// Initializes ClientAttributes.
    ///
    /// - parameter appIdSuffix: A suffix that will be appended to the app_id (default: "").
    init(appIdSuffix: String = "") {
        if let baseAppID = Bundle.main.bundleIdentifier {
            self.appID = baseAppID + appIdSuffix
        } else {
            self.appID = "unknown"
        }
    }
}

extension ClientAttributes: FieldProvider {
    public func getFields() -> Fields {
        return [
            /// The bundle ID which identifies the running app (e.g. com.zimride.instant).
            "app_id": self.appID,
            /// Operating system. Always iOS for this code path.
            "os": "iOS",
            /// The operating system version (e.g. 13.0).
            "os_version": self.osVersion,
            /// The release version of the app (e.g. 1.2.33).
            "app_version": self.appVersion,
            /// The build number (e.g. 1234 or 1.2.33.12314).
            "_build_number": self.buildNumber,
        ]
    }
}
