// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge

struct ProvisioningProfileInspector {
    private enum Constants {
        static let resourceName = "embedded"
        static let resourceExtension = "mobileprovision"
        static let entitlements = "Entitlements"
        static let getTaskAllow = "get-task-allow"
        static let provisionsAllDevices = "ProvisionsAllDevices"
        static let provisionedDevices = "ProvisionedDevices"
        static let teamIdentifier = "TeamIdentifier"
        static let teamIdentifierEntitlement = "com.apple.developer.team-identifier"
        static let plistStart = "<?xml"
        static let plistEnd = "</plist>"
    }

    private let bundle: Bundle

    init(bundle: Bundle = .main) {
        self.bundle = bundle
    }

    func inspect() -> AppDistributionInfo? {
        guard
            let profileURL = self.bundle.url(
                forResource: Constants.resourceName,
                withExtension: Constants.resourceExtension
            ),
            let profileData = try? Data(contentsOf: profileURL),
            let profilePayload = Self.propertyListPayload(from: profileData),
            let propertyList = try? PropertyListSerialization.propertyList(
                from: profilePayload,
                options: [],
                format: nil
            ),
            let profile = propertyList as? [String: Any]
        else {
            return nil
        }

        return Self.distributionInfo(from: profile)
    }

    static func distributionInfo(from profile: [String: Any]) -> AppDistributionInfo {
        let entitlements = profile[Constants.entitlements] as? [String: Any]
        let teamIdentifier = (profile[Constants.teamIdentifier] as? [String])?.first
            ?? entitlements?[Constants.teamIdentifierEntitlement] as? String
        let environment: AppEnvironment

        if entitlements?[Constants.getTaskAllow] as? Bool == true {
            environment = .debug
        } else if profile[Constants.provisionsAllDevices] as? Bool == true {
            environment = .enterprise
        } else if profile[Constants.provisionedDevices] as? [String] != nil {
            environment = .adHoc
        } else {
            environment = .unknown
        }

        return AppDistributionInfo(environment: environment, teamIdentifier: teamIdentifier)
    }

    /// Extracts the XML plist we care about from the rest of the profile's contents.
    ///
    /// - parameter profileData: the raw contents of the provisioning profile.
    ///
    /// - returns: the plist portion, or nil if the profile doesn't contain one.
    static func propertyListPayload(from profileData: Data) -> Data? {
        let start = Data(Constants.plistStart.utf8)
        let end = Data(Constants.plistEnd.utf8)
        guard
            let startRange = profileData.range(of: start),
            let endRange = profileData.range(
                of: end,
                options: [],
                in: startRange.lowerBound ..< profileData.endIndex
            )
        else {
            return nil
        }
        return profileData.subdata(in: startRange.lowerBound ..< endRange.upperBound)
    }
}
