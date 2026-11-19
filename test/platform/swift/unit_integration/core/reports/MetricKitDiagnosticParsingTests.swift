// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import CaptureLoggerBridge
import XCTest

final class MetricKitDiagnosticParsingTests: XCTestCase {
    private var parsing: MetricKitDiagnosticParsing!

    override func setUp() {
        self.parsing = MetricKitDiagnosticParsing()
    }

    override func tearDown() {
        self.parsing = nil
    }

    // MARK: - nameForExceptionType(signal:)

    func testNameForExceptionTypeSignalPrefersExceptionType() {
        // EXC_BAD_ACCESS, SIGSEGV
        XCTAssertEqual(self.parsing.name(forExceptionType: 1, signal: SIGSEGV), "EXC_BAD_ACCESS")
    }

    func testNameForExceptionTypeSignalFallsBackToSignal() {
        XCTAssertEqual(self.parsing.name(forExceptionType: -1, signal: SIGKILL), "SIGKILL")
    }

    func testNameForExceptionTypeSignalNilWhenNeitherRecognized() {
        XCTAssertNil(self.parsing.name(forExceptionType: -1, signal: -1))
    }

    // MARK: - isWatchdogHangTermination

    func testIsWatchdogHangTerminationTrueFor8badf00dTerminationReason() {
        let isHang = self.parsing.isWatchdogHangTermination(
            withExceptionType: 10, // EXC_CRASH
            signal: NSNumber(value: SIGKILL),
            terminationReason: "domain:10 code:0x8BADF00D explanation:app took too long",
            exceptionCode: 0
        )
        XCTAssertTrue(isHang)
    }

    func testIsWatchdogHangTerminationTrueWhenNoTerminationReasonAndZeroExceptionCode() {
        let isHang = self.parsing.isWatchdogHangTermination(
            withExceptionType: 10, // EXC_CRASH
            signal: NSNumber(value: SIGKILL),
            terminationReason: nil,
            exceptionCode: 0
        )
        XCTAssertTrue(isHang)
    }

    func testIsWatchdogHangTerminationFalseForNonAnrWatchdogKill() {
        // e.g. a thermal "Cool Off" termination: watchdogd-issued SIGKILL, but not an app hang.
        let isHang = self.parsing.isWatchdogHangTermination(
            withExceptionType: 10, // EXC_CRASH
            signal: NSNumber(value: SIGKILL),
            terminationReason: "thermal-cool-off-termination",
            exceptionCode: 1
        )
        XCTAssertFalse(isHang)
    }

    func testIsWatchdogHangTerminationFalseWhenNoTerminationReasonButNonZeroExceptionCode() {
        let isHang = self.parsing.isWatchdogHangTermination(
            withExceptionType: 10, // EXC_CRASH
            signal: NSNumber(value: SIGKILL),
            terminationReason: nil,
            exceptionCode: 5
        )
        XCTAssertFalse(isHang)
    }

    func testIsWatchdogHangTerminationFalseWhenSignalIsNotSigkill() {
        let isHang = self.parsing.isWatchdogHangTermination(
            withExceptionType: 10, // EXC_CRASH
            signal: NSNumber(value: SIGSEGV),
            terminationReason: nil,
            exceptionCode: 0
        )
        XCTAssertFalse(isHang)
    }

    func testIsWatchdogHangTerminationFalseWhenExceptionTypeIsNotExcCrash() {
        let isHang = self.parsing.isWatchdogHangTermination(
            withExceptionType: 1, // EXC_BAD_ACCESS
            signal: NSNumber(value: SIGKILL),
            terminationReason: nil,
            exceptionCode: 0
        )
        XCTAssertFalse(isHang)
    }

    func testIsWatchdogHangTerminationFalseWhenExceptionTypeOrSignalAreNil() {
        XCTAssertFalse(
            self.parsing.isWatchdogHangTermination(
                withExceptionType: nil,
                signal: NSNumber(value: SIGKILL),
                terminationReason: nil,
                exceptionCode: 0
            ))
        XCTAssertFalse(
            self.parsing.isWatchdogHangTermination(
                withExceptionType: 10,
                signal: nil,
                terminationReason: nil,
                exceptionCode: 0
            ))
    }

    // MARK: - reasonForCrash

    func testReasonForCrashPrefersMetricKitReason() {
        let reason = self.parsing.reasonForCrash(
            withName: "EXC_BAD_ACCESS",
            metricKitReasonName: "NSInvalidArgumentException",
            metricKitReasonComposedMessage: "some message",
            capturedCrashName: "ShouldBeIgnored",
            capturedCrashReason: "ShouldBeIgnored",
            terminationReason: nil,
            virtualMemoryRegionInfo: nil,
            exceptionCode: nil,
            signal: SIGSEGV
        )
        XCTAssertEqual(reason, "NSInvalidArgumentException: some message")
    }

    func testReasonForCrashFallsBackToCapturedCrashWhenNoMetricKitReason() {
        let reason = self.parsing.reasonForCrash(
            withName: "EXC_BAD_ACCESS",
            metricKitReasonName: nil,
            metricKitReasonComposedMessage: nil,
            capturedCrashName: "NSInvalidArgumentException",
            capturedCrashReason: "boom",
            terminationReason: nil,
            virtualMemoryRegionInfo: nil,
            exceptionCode: nil,
            signal: SIGSEGV
        )
        XCTAssertEqual(reason, "NSInvalidArgumentException: boom")
    }

    func testReasonForCrashJoinsMultipleComponents() {
        let reason = self.parsing.reasonForCrash(
            withName: "EXC_BAD_ACCESS",
            metricKitReasonName: nil,
            metricKitReasonComposedMessage: nil,
            capturedCrashName: nil,
            capturedCrashReason: nil,
            terminationReason: "termination info",
            virtualMemoryRegionInfo: "vm region info",
            exceptionCode: nil,
            signal: SIGSEGV
        )
        XCTAssertEqual(reason, "termination info.\nvm region info")
    }

    func testReasonForCrashFallsBackToExceptionCodeAndSignal() {
        let reason = self.parsing.reasonForCrash(
            withName: "EXC_BAD_ACCESS",
            metricKitReasonName: nil,
            metricKitReasonComposedMessage: nil,
            capturedCrashName: nil,
            capturedCrashReason: nil,
            terminationReason: nil,
            virtualMemoryRegionInfo: nil,
            exceptionCode: 1,
            signal: SIGSEGV
        )
        XCTAssertEqual(reason, "code: 1, signal: SIGSEGV")
    }

    func testReasonForCrashFallsBackToSignalNameUnlessSameAsName() {
        let reason = self.parsing.reasonForCrash(
            withName: "SIGSEGV",
            metricKitReasonName: nil,
            metricKitReasonComposedMessage: nil,
            capturedCrashName: nil,
            capturedCrashReason: nil,
            terminationReason: nil,
            virtualMemoryRegionInfo: nil,
            exceptionCode: nil,
            signal: SIGSEGV
        )
        XCTAssertNil(reason)
    }

    // MARK: - nameForReportType

    func testNameForReportType() {
        XCTAssertEqual(self.parsing.name(for: .nativeCrash), "crash")
        XCTAssertEqual(self.parsing.name(for: .appNotResponding), "anr")
        XCTAssertEqual(self.parsing.name(for: .none), "unknown")
    }

    // MARK: - nameForExceptionType

    func testNameForExceptionTypeKnownValue() {
        // EXC_BAD_ACCESS
        XCTAssertEqual(self.parsing.name(forExceptionType: 1), "EXC_BAD_ACCESS")
    }

    func testNameForExceptionTypeCrash() {
        // EXC_CRASH
        XCTAssertEqual(self.parsing.name(forExceptionType: 10), "EXC_CRASH")
    }

    func testNameForExceptionTypeUnknownValue() {
        XCTAssertNil(self.parsing.name(forExceptionType: -1))
    }

    // MARK: - nameForSignal

    func testNameForSignalKnownValue() {
        XCTAssertEqual(self.parsing.name(forSignal: SIGSEGV), "SIGSEGV")
        XCTAssertEqual(self.parsing.name(forSignal: SIGKILL), "SIGKILL")
        XCTAssertEqual(self.parsing.name(forSignal: SIGABRT), "SIGABRT")
    }

    func testNameForSignalUnknownValue() {
        XCTAssertNil(self.parsing.name(forSignal: -1))
    }

    // MARK: - parseTerminationContext

    func testParseTerminationContextEmptyString() {
        XCTAssertEqual(self.parsing.parseTerminationContext(""), [:])
    }

    func testParseTerminationContextHeaderOnly() {
        let reason = "domain:com.apple.watchdogd code:0x8badf00d explanation:some explanation here"
        let result = self.parsing.parseTerminationContext(reason)

        XCTAssertEqual(result["domain"], "com.apple.watchdogd")
        XCTAssertEqual(result["code"], "0x8badf00d")
        XCTAssertEqual(result["explanation"], "some explanation here")
    }

    func testParseTerminationContextWatchdogLines() {
        let reason = """
        domain:com.apple.watchdogd code:0x8badf00d explanation:app took too long
        ProcessVisibility: Foreground
        ProcessState: Running
        WatchdogEvent: com.apple.springboard.applicationresponseevent
        WatchdogVisibility: Foreground
        """
        let result = self.parsing.parseTerminationContext(reason)

        XCTAssertEqual(result["process_visibility"], "Foreground")
        XCTAssertEqual(result["process_state"], "Running")
        XCTAssertEqual(result["watchdog_event"], "com.apple.springboard.applicationresponseevent")
        XCTAssertEqual(result["watchdog_visibility"], "Foreground")
    }

    func testParseTerminationContextNoHeaderMatch() {
        let result = self.parsing.parseTerminationContext("some unrelated free-form text")
        XCTAssertEqual(result, [:])
    }

    // MARK: - timestampComponents

    func testTimestampComponentsSplitsWholeAndFractionalSeconds() {
        var seconds: UInt64 = 0
        var nanos: UInt32 = 0
        self.parsing.timestampComponents(for: 1_700_000_000.5, seconds: &seconds, nanos: &nanos)

        XCTAssertEqual(seconds, 1_700_000_000)
        XCTAssertEqual(nanos, 500_000_000, accuracy: 1000)
    }

    func testTimestampComponentsWholeNumber() {
        var seconds: UInt64 = 0
        var nanos: UInt32 = 0
        self.parsing.timestampComponents(for: 42.0, seconds: &seconds, nanos: &nanos)

        XCTAssertEqual(seconds, 42)
        XCTAssertEqual(nanos, 0)
    }

    // MARK: - architectureConstant

    func testArchitectureConstantArm64() {
        XCTAssertEqual(self.parsing.architectureConstant(for: "arm64e"), 2)
        XCTAssertEqual(self.parsing.architectureConstant(for: "arm64"), 2)
    }

    func testArchitectureConstantX86_64() {
        XCTAssertEqual(self.parsing.architectureConstant(for: "x86_64"), 4)
    }

    func testArchitectureConstantNil() {
        XCTAssertEqual(self.parsing.architectureConstant(for: nil), 0)
    }
}
