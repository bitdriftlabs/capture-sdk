// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
@testable import CaptureLoggerBridge
import XCTest

class ProvisioningProfileInspectorTests: XCTestCase {
    private var entitlements: [String: Any]!
    private var profile: [String: Any]!
    private var profileContents: Data!

    private var distributionInfo: AppDistributionInfo!
    private var payload: Data?

    override func setUp() {
        entitlements = [:]
        profile = [:]
        profileContents = Data()
    }

    func testOnDebugEntitlementResolvesDebugEnvironment() {
        givenProfileWithDebugEntitlement()
        whenInspectingProfile()
        thenEnvironmentIs(.debug)
    }

    func testOnProvisionsAllDevicesResolvesEnterpriseEnvironment() {
        givenProfileProvisioningAllDevices()
        whenInspectingProfile()
        thenEnvironmentIs(.enterprise)
    }

    func testOnProvisionedDevicesResolvesAdHocEnvironment() {
        givenProfileWithProvisionedDevices()
        whenInspectingProfile()
        thenEnvironmentIs(.adHoc)
    }

    func testOnProfileWithoutDistributionHintsResolvesUnknownEnvironment() {
        whenInspectingProfile()
        thenEnvironmentIs(.unknown)
    }

    func testDebugEntitlementTakesPrecedenceOverDistributionProfile() {
        givenProfileWithDebugEntitlement()
        givenProfileProvisioningAllDevices()
        givenProfileWithProvisionedDevices()
        whenInspectingProfile()
        thenEnvironmentIs(.debug)
    }

    func testReadsTeamIdentifierFromProfile() {
        givenProfileWithTeamIdentifier("TEAM123456")
        whenInspectingProfile()
        thenTeamIdentifierIs("TEAM123456")
    }

    func testReadsTeamIdentifierFromEntitlementWhenProfileHasNone() {
        givenProfileWithTeamIdentifierEntitlement("TEAM654321")
        whenInspectingProfile()
        thenTeamIdentifierIs("TEAM654321")
    }

    func testPrefersProfileTeamIdentifierOverEntitlement() {
        givenProfileWithTeamIdentifier("FROM_PROFILE")
        givenProfileWithTeamIdentifierEntitlement("FROM_ENTITLEMENT")
        whenInspectingProfile()
        thenTeamIdentifierIs("FROM_PROFILE")
    }

    func testOnProfileWithoutTeamIdentifierReportsNone() {
        whenInspectingProfile()
        thenTeamIdentifierIsMissing()
    }

    func testExtractsPropertyListFromProfileContents() throws {
        givenProfileContentsWithPropertyList()
        whenExtractingPropertyListPayload()
        try thenPayloadIsTheEmbeddedPropertyList()
    }

    func testOnContentsWithoutPropertyListExtractsNothing() {
        givenProfileContentsWithoutPropertyList()
        whenExtractingPropertyListPayload()
        thenPayloadIsMissing()
    }

    func testOnEmptyContentsExtractsNothing() {
        whenExtractingPropertyListPayload()
        thenPayloadIsMissing()
    }
}

private extension ProvisioningProfileInspectorTests {
    func givenProfileWithDebugEntitlement() {
        entitlements["get-task-allow"] = true
    }

    func givenProfileProvisioningAllDevices() {
        profile["ProvisionsAllDevices"] = true
    }

    func givenProfileWithProvisionedDevices() {
        profile["ProvisionedDevices"] = ["device-1", "device-2"]
    }

    func givenProfileWithTeamIdentifier(_ teamIdentifier: String) {
        profile["TeamIdentifier"] = [teamIdentifier]
    }

    func givenProfileWithTeamIdentifierEntitlement(_ teamIdentifier: String) {
        entitlements["com.apple.developer.team-identifier"] = teamIdentifier
    }

    func givenProfileContentsWithPropertyList() {
        profileContents = Data(
            """
            signature<?xml version="1.0"?><plist version="1.0">\
            <dict><key>Name</key><string>Profile</string></dict></plist>trailer
            """.utf8
        )
    }

    func givenProfileContentsWithoutPropertyList() {
        profileContents = Data("signature only".utf8)
    }

    func whenInspectingProfile() {
        profile["Entitlements"] = entitlements
        distributionInfo = ProvisioningProfileInspector.distributionInfo(from: profile)
    }

    func whenExtractingPropertyListPayload() {
        payload = ProvisioningProfileInspector.propertyListPayload(from: profileContents)
    }

    func thenEnvironmentIs(_ environment: AppEnvironment) {
        XCTAssertEqual(environment, distributionInfo.environment)
    }

    func thenTeamIdentifierIs(_ teamIdentifier: String) {
        XCTAssertEqual(teamIdentifier, distributionInfo.teamIdentifier)
    }

    func thenTeamIdentifierIsMissing() {
        XCTAssertNil(distributionInfo.teamIdentifier)
    }

    func thenPayloadIsTheEmbeddedPropertyList() throws {
        let payload = try XCTUnwrap(self.payload)
        let propertyList = try PropertyListSerialization.propertyList(from: payload, options: [], format: nil)

        XCTAssertEqual("Profile", (propertyList as? [String: String])?["Name"])
    }

    func thenPayloadIsMissing() {
        XCTAssertNil(payload)
    }
}
