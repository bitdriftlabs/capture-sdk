// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
@testable import CaptureLoggerBridge
@testable import CaptureMocks
import XCTest

final class CrashReporterServiceTests: XCTestCase {
    private var ksCrashHandler: MockKSCrashHandler!
    private var bitdriftCrashHandler: MockBitdriftCrashHandler!
    private var logger: MockCoreLogging!
    private var sdkBaseURL: URL!
    private var sut: CrashReporterService!

    override func setUp() {
        logger = MockCoreLogging()
        ksCrashHandler = MockKSCrashHandler()
        bitdriftCrashHandler = MockBitdriftCrashHandler()
        sdkBaseURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try? FileManager.default.createDirectory(at: sdkBaseURL, withIntermediateDirectories: true)
    }

    override func tearDown() {
        Logger.issueReporterInitResult = (.notInitialized, 0)
        Logger.diagnosticReporter.update { $0 = nil }
        Logger.previousRunInfoValue = nil
        try? FileManager.default.removeItem(at: sdkBaseURL)
    }

    // MARK: - Simulator

    func testSetupOnSimulatorSetsUnsupportedHardwareAndClearsLastRun() {
        givenCrashReporterService(environment: .simulator())
        whenInvokingSetup()
        thenIssueReporterInitStateIs(.initialized(.unsupportedHardware))
    }

    // MARK: - Runtime state

    func testSetupReturnsMonitoringByDefaultWhenNoConfigFile() {
        givenCrashReporterService()
        whenInvokingSetup()
        thenIssueReporterInitStateIs(.initialized(.monitoring))
    }

    func testSetupReturnsMonitoringWhenCrashReportingFlagIsEnabled() {
        givenCrashReporterService()
        givenConfigFile("crash_reporting.enabled,true")
        whenInvokingSetup()
        thenIssueReporterInitStateIs(.initialized(.monitoring))
    }

    func testSetupReturnsRuntimeNotEnabledWhenCrashReportingFlagIsDisabled() {
        givenCrashReporterService()
        givenConfigFile("crash_reporting.enabled,false")
        whenInvokingSetup()
        thenIssueReporterInitStateIs(.initialized(.runtimeNotEnabled))
    }

    func testSetupReturnsRuntimeInvalidWhenConfigIsMalformed() {
        givenCrashReporterService()
        givenConfigFile("this_shouldnt_work")
        whenInvokingSetup()
        thenIssueReporterInitStateIs(.initialized(.runtimeInvalid))
    }

    func testSetupReturnsRuntimeMissingFlagWhenCrashReportingKeyIsAbsent() {
        givenCrashReporterService()
        givenConfigFile("some_other_key,true")
        whenInvokingSetup()
        thenIssueReporterInitStateIs(.initialized(.runtimeMissingFlag))
    }

    // MARK: - Handler initialization

    func testSetupCallsConfigureAndStartOnKSCrashHandler() {
        givenCrashReporterService()
        whenInvokingSetup()
        thenKSCrashHandlerIsConfigured()
        thenKSCrashHandlerIsStarted()
    }

    func testSetupCallsConfigureAndStartOnBitdriftHandlerWhenEnabled() {
        givenCrashReporterService()
        whenInvokingSetup()
        thenBitdriftCrashHandlerIsConfigured()
        thenBitdriftCrashHandlerIsStarted()
    }

    func testSetupDoesNotCallBitdriftHandlerWhenDisabled() {
        givenCrashReporterService(bitdriftCrashReporterEnabled: false)
        whenInvokingSetup()
        thenBitdriftCrashHandlerIsNotConfigured()
        thenBitdriftCrashHandlerIsNotStarted()
    }

    func testSetupContinuesWithBitdriftIfKSCrashConfigurationFails() {
        givenCrashReporterService()
        givenKSCrashThrowsOnConfigure()
        whenInvokingSetup()
        thenBitdriftCrashHandlerIsConfigured()
    }

    func testSetupContinuesWithDiagnosticReporterIfBitdriftConfigurationFails() {
        givenCrashReporterService()
        givenBitdriftThrowsOnConfigure()
        whenInvokingSetup()
        thenDiagnosticReporterIsCreated()
    }

    // MARK: - Stop

    func testStopWhenBitdriftDisabledOnlyStopsKSCrash() {
        givenCrashReporterService(bitdriftCrashReporterEnabled: false)
        whenInvokingSetup()
        whenInvokingStop()
        thenKSCrashHandlerStopsCrashReporter()
        thenBitdriftCrashHandlerDoesntStopCrashReporter()
    }

    func testStopWhenBitdriftEnabledStopsBothHandlers() {
        givenCrashReporterService()
        whenInvokingSetup()
        whenInvokingStop()
        thenKSCrashHandlerStopsCrashReporter()
        thenBitdriftCrashHandlerStopsCrashReporter()
    }

    // MARK: - cachedPreviousCrash

    func testCachedPreviousCrashWhenBitdriftDisabledReturnsNilWithoutCallingBitdriftHandler() {
        givenCrashReporterService(bitdriftCrashReporterEnabled: false)
        whenInvokingSetup()
        thenCachedPreviousCrashIsNil()
        thenBitdriftCrashHandlerDoesntInvokeCachedPreviousCrash()
    }

    // MARK: - cachedCrashDate

    func testCachedCrashDateUsesBitdriftDateWhenBitdriftEnabledAndHasCrashDate() throws {
        let bitdriftDate = Date(timeIntervalSinceNow: -100)
        let kscrashDate = Date(timeIntervalSinceNow: -200)
        givenCrashReporterService()
        givenBitdriftCachedCrashDate(bitdriftDate)
        givenKSCrashCachedCrashDate(kscrashDate)
        whenInvokingSetup()
        try thenCachedCrashDateIs(bitdriftDate)
    }

    func testCachedCrashDateFallsBackToKSCrashWhenBitdriftDisabled() throws {
        let bitdriftDate = Date(timeIntervalSinceNow: -100)
        let kscrashDate = Date(timeIntervalSinceNow: -200)
        givenCrashReporterService(bitdriftCrashReporterEnabled: false)
        givenBitdriftCachedCrashDate(bitdriftDate)
        givenKSCrashCachedCrashDate(kscrashDate)
        whenInvokingSetup()
        try thenCachedCrashDateIs(kscrashDate)
    }

    func testCachedCrashDateFallsBackToKSCrashWhenBitdriftEnabledButNoCrashDate() throws {
        let kscrashDate = Date(timeIntervalSinceNow: -200)
        givenCrashReporterService()
        givenBitdriftNoCachedCrashDate()
        givenKSCrashCachedCrashDate(kscrashDate)
        whenInvokingSetup()
        try thenCachedCrashDateIs(kscrashDate)
    }

    // MARK: - enhancedMetricKitReport

    func testEnhancedMetricKitReportDelegatesToKSCrashHandler() {
        let report = ["key": "value"]
        givenCrashReporterService()
        thenEnhancedMetricKitReportDelegatesToKSCrash(report)
    }

    // MARK: - makeDiagnosticReporter

    func testSetupCreatesDiagnosticReporter() {
        givenCrashReporterService()
        whenInvokingSetup()
        thenDiagnosticReporterIsCreated()
    }

    // MARK: - degraded

    func testSetupSetsDegradedWhenKSCrashConfigurationFails() {
        givenCrashReporterService()
        givenKSCrashThrowsOnConfigure()
        whenInvokingSetup()
        thenIssueReporterInitStateIs(.initialized(.degraded))
    }

    // MARK: - previousRunInfo (legacy path)

    func testPreviousRunInfoIsUnknownOnSimulator() {
        givenCrashReporterService(environment: .simulator())
        whenInvokingSetup()
        thenPreviousRunInfoIs(.unknown)
    }

    func testPreviousRunInfoIsUnknownWhenNoCrash() {
        givenCrashReporterService()
        whenInvokingSetup()
        thenPreviousRunInfoIs(.unknown)
    }

    func testPreviousRunInfoIsFatalCrashWhenKSCrashReportsCrash() {
        givenCrashReporterService()
        givenKSCrashDidCrashLastLaunch(true)
        whenInvokingSetup()
        thenPreviousRunInfoIs(PreviousRunInfo(terminationReason: .fatalCrash))
    }

    func testPreviousRunInfoIsUnknownWhenRuntimeDisablesCrashReporting() {
        givenCrashReporterService()
        givenConfigFile("crash_reporting.enabled,false")
        whenInvokingSetup()
        thenPreviousRunInfoIs(.unknown)
    }

    // MARK: - previousRunInfo (revamped path)

    func testPreviousRunInfoComesFromControllerWhenRevampedEnabled() throws {
        let controller = try XCTUnwrap(PreviousRunInfoController(baseDirectory: sdkBaseURL, osVersion: "18.0"))
        givenCrashReporterService(previousRunInfoRevamped: true, previousRunInfoController: controller)
        whenInvokingSetup()
        // on first launch, controller always resolves to .unknown regardless of didCrashLastLaunch
        thenPreviousRunInfoIs(.unknown)
    }

    func testPreviousRunInfoIsFatalCrashFromControllerOnSecondLaunchWithCrash() throws {
        // Simulate first launch to write state to disk
        let firstController = try XCTUnwrap(
            PreviousRunInfoController(baseDirectory: sdkBaseURL, osVersion: "18.0")
        )
        firstController.resolve(didCrashLastLaunch: false)

        let controller = try XCTUnwrap(PreviousRunInfoController(baseDirectory: sdkBaseURL, osVersion: "18.0"))
        givenCrashReporterService(previousRunInfoRevamped: true, previousRunInfoController: controller)
        givenKSCrashDidCrashLastLaunch(true)
        whenInvokingSetup()
        thenPreviousRunInfoIs(PreviousRunInfo(terminationReason: .fatalCrash))
    }
}

private extension CrashReporterServiceTests {
    func givenCrashReporterService(
        bitdriftCrashReporterEnabled: Bool = true,
        previousRunInfoRevamped: Bool = false,
        environment: MockEnvironment = .device(),
        previousRunInfoController: PreviousRunInfoController? = nil
    ) {
        logger.mockRuntimeVariable(.bdCrashReporter, with: bitdriftCrashReporterEnabled)
        logger.mockRuntimeVariable(.previousRunInfoRevamped, with: previousRunInfoRevamped)
        sut = CrashReporterService(
            previousRunInfoController: previousRunInfoController,
            ksCrashHandler: ksCrashHandler,
            bitdriftCrashHandler: bitdriftCrashHandler,
            environment: environment
        )
    }

    func givenKSCrashDidCrashLastLaunch(_ crashed: Bool) {
        ksCrashHandler.didCrashLastLaunchValue = NSNumber(value: crashed)
    }

    // TODO: move all of this csv logic into an abstraction that can be injected; this knows a lot of internals
    func givenConfigFile(_ content: String) {
        let reportsDir = sdkBaseURL.appendingPathComponent("reports", isDirectory: true)
        try? FileManager.default.createDirectory(at: reportsDir, withIntermediateDirectories: true)
        try? content.write(
            to: reportsDir.appendingPathComponent("config.csv"),
            atomically: true,
            encoding: .utf8
        )
    }

    func givenKSCrashCachedCrashDate(_ date: Date) {
        ksCrashHandler.cachedCrashDateValue = date
    }

    func givenBitdriftCachedCrashDate(_ date: Date) {
        bitdriftCrashHandler.cachedCrashDateValue = date
    }

    func givenBitdriftNoCachedCrashDate() {
        bitdriftCrashHandler.cachedCrashDateValue = nil
    }

    func givenBitdriftNoPreviousCrash() {
        bitdriftCrashHandler.previousCrash = nil
    }

    func givenKSCrashThrowsOnConfigure() {
        ksCrashHandler.shouldThrowOnConfigure = true
    }

    func givenBitdriftThrowsOnConfigure() {
        bitdriftCrashHandler.shouldThrowOnConfigure = true
    }

    func whenInvokingSetup() {
        sut.setup(sdkBaseURL: sdkBaseURL, underlyingLogger: logger)
    }

    func whenInvokingStop() {
        sut.stop()
    }

    func thenIssueReporterInitStateIs(_ expected: IssueReporterInitState) {
        XCTAssertEqual(Logger.issueReporterInitResult.0, expected)
    }

    func thenKSCrashHandlerIsConfigured() {
        XCTAssertTrue(ksCrashHandler.didConfigure)
    }

    func thenKSCrashHandlerIsStarted() {
        XCTAssertTrue(ksCrashHandler.didStart)
    }

    func thenBitdriftCrashHandlerIsConfigured() {
        XCTAssertTrue(bitdriftCrashHandler.didConfigure)
    }

    func thenBitdriftCrashHandlerIsStarted() {
        XCTAssertTrue(bitdriftCrashHandler.didStart)
    }

    func thenBitdriftCrashHandlerIsNotConfigured() {
        XCTAssertFalse(bitdriftCrashHandler.didConfigure)
    }

    func thenBitdriftCrashHandlerIsNotStarted() {
        XCTAssertFalse(bitdriftCrashHandler.didStart)
    }

    func thenKSCrashHandlerStopsCrashReporter() {
        XCTAssertTrue(ksCrashHandler.didStop)
    }

    func thenBitdriftCrashHandlerStopsCrashReporter() {
        XCTAssertTrue(bitdriftCrashHandler.didStop)
    }

    func thenBitdriftCrashHandlerDoesntStopCrashReporter() {
        XCTAssertFalse(bitdriftCrashHandler.didStop)
    }

    func thenCachedPreviousCrashIsNil() {
        XCTAssertNil(sut.cachedPreviousCrash())
    }

    func thenBitdriftCrashHandlerDoesntInvokeCachedPreviousCrash() {
        XCTAssertFalse(bitdriftCrashHandler.didCallCachedPreviousCrash)
    }

    func thenCachedCrashDateIs(_ expected: Date) throws {
        let date = try XCTUnwrap(sut.cachedCrashDate())
        XCTAssertEqual(date, expected)
    }

    func thenEnhancedMetricKitReportDelegatesToKSCrash(_ report: [String: Any]) {
        _ = sut.enhancedMetricKitReport(report, useStackOverlapMatching: false, summaryOut: nil)
        XCTAssertTrue(ksCrashHandler.didCallEnhancedMetricKitReport)
    }

    func thenDiagnosticReporterIsCreated() {
        XCTAssertNotNil(Logger.diagnosticReporter.load())
    }

    func thenPreviousRunInfoIs(_ expected: PreviousRunInfo) {
        XCTAssertEqual(Logger.previousRunInfoValue, expected)
    }

}
