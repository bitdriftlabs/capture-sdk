// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import BitdriftEnhancedCrashData
@testable import CaptureLoggerBridge
import FlatBuffers
import XCTest

private typealias FBReport = bitdrift_public_fbs_issue_reporting_v1_Report
private typealias FBReportType = bitdrift_public_fbs_issue_reporting_v1_ReportType
private typealias FBArch = bitdrift_public_fbs_issue_reporting_v1_Architecture
private typealias FBFrame = bitdrift_public_fbs_issue_reporting_v1_Frame

final class DiagnosticReportWriterTests: XCTestCase {
    private var outputDir: URL!
    private var writer: DiagnosticReportWriter!

    override func setUpWithError() throws {
        self.outputDir = try createTempDir()
        self.writer = self.makeWriter()
    }

    override func tearDownWithError() throws {
        try FileManager.default.removeItem(at: self.outputDir)
    }

    func testWriteCrashReportWritesThreadsErrorAndBinaryImages() throws {
        self.writer.writeCrashReport(
            with: .nativeCrash,
            dict: self.makeCrashDict(callStacks: [self.makeThread()]),
            name: "EXC_BAD_ACCESS",
            reason: "SIGSEGV",
            machExceptionType: nil,
            exceptionCode: nil,
            signal: nil,
            terminationReason: nil,
            capturedCrash: nil,
            metadata: self.makeMetadata(),
            applicationVersion: "1.0",
            timestamp: 1_700_000_000
        )

        let report = try self.loadOnlyReport()
        XCTAssertEqual(FBReportType.nativecrash, report.type)
        XCTAssertEqual(1, report.errorsCount)

        let error = try XCTUnwrap(report.errors(at: 0))
        XCTAssertEqual("EXC_BAD_ACCESS", error.name!)
        XCTAssertEqual("SIGSEGV", error.reason!)
        XCTAssertEqual(1, error.stackTraceCount)

        XCTAssertEqual(1, report.binaryImagesCount)
        let image = try XCTUnwrap(report.binaryImages(at: 0))
        XCTAssertEqual("TestBinary", image.path!)
    }

    func testWriteCrashReportAttachesRegistersToInnermostFrame() throws {
        self.writer.writeCrashReport(
            with: .nativeCrash,
            dict: self.makeCrashDict(callStacks: [self.makeThread(registers: [
                "sp": 0x5678,
                "pc": 0x1234,
                "lr": 0x9ABC,
            ])]),
            name: "EXC_BAD_ACCESS",
            reason: "SIGSEGV",
            machExceptionType: nil,
            exceptionCode: nil,
            signal: nil,
            terminationReason: nil,
            capturedCrash: nil,
            metadata: self.makeMetadata(),
            applicationVersion: "1.0",
            timestamp: 1_700_000_000
        )

        let report = try self.loadOnlyReport()
        let errorFrame = try XCTUnwrap(XCTUnwrap(report.errors(at: 0)).stackTrace(at: 0))
        let threadFrame = try XCTUnwrap(
            XCTUnwrap(XCTUnwrap(report.threadDetails).threads(at: 0)).stackTrace(at: 0))

        for frame in [errorFrame, threadFrame] {
            let registers = self.registerPairs(of: frame)
            XCTAssertEqual(["lr", "pc", "sp"], registers.map(\.0))
            XCTAssertEqual([0x9ABC, 0x1234, 0x5678], registers.map(\.1))
        }
    }

    func testWriteCrashReportWritesNoRegistersWhenThreadHasNone() throws {
        self.writer.writeCrashReport(
            with: .nativeCrash,
            dict: self.makeCrashDict(callStacks: [self.makeThread()]),
            name: "EXC_BAD_ACCESS",
            reason: "SIGSEGV",
            machExceptionType: nil,
            exceptionCode: nil,
            signal: nil,
            terminationReason: nil,
            capturedCrash: nil,
            metadata: self.makeMetadata(),
            applicationVersion: "1.0",
            timestamp: 1_700_000_000
        )

        let report = try self.loadOnlyReport()
        let frame = try XCTUnwrap(XCTUnwrap(report.errors(at: 0)).stackTrace(at: 0))

        XCTAssertEqual(0, frame.registersCount)
    }

    func testWriteCrashReportHandlesEmptyThreadsByStillWritingAnError() throws {
        self.writer.writeCrashReport(
            with: .nativeCrash,
            dict: self.makeCrashDict(callStacks: []),
            name: "SIGABRT",
            reason: nil,
            machExceptionType: nil,
            exceptionCode: nil,
            signal: nil,
            terminationReason: nil,
            capturedCrash: nil,
            metadata: self.makeMetadata(),
            applicationVersion: "1.0",
            timestamp: 1_700_000_000
        )

        let report = try self.loadOnlyReport()
        XCTAssertEqual(1, report.errorsCount)
        XCTAssertEqual("SIGABRT", try XCTUnwrap(report.errors(at: 0)).name!)
    }

    func testWriteNonCrashReportUsesGivenReportTypeAndNameReason() throws {
        self.writer.writeNonCrashReport(
            with: .nativeCrash,
            dict: self.makeCrashDict(callStacks: [self.makeThread()]),
            name: "EXC_RESOURCE",
            reason: "Out Of Memory",
            order: .outerToInner,
            metadata: self.makeMetadata(),
            applicationVersion: "1.0",
            timestamp: 1_700_000_000
        )

        let report = try self.loadOnlyReport()
        XCTAssertEqual(FBReportType.nativecrash, report.type)
        let error = try XCTUnwrap(report.errors(at: 0))
        XCTAssertEqual("EXC_RESOURCE", error.name!)
        XCTAssertEqual("Out Of Memory", error.reason!)
    }

    func testWriteCrashReportSerializesAppAndDeviceMetricsFromMetadata() throws {
        self.writer.writeCrashReport(
            with: .nativeCrash,
            dict: self.makeCrashDict(callStacks: []),
            name: nil,
            reason: nil,
            machExceptionType: nil,
            exceptionCode: nil,
            signal: nil,
            terminationReason: nil,
            capturedCrash: nil,
            metadata: self.makeMetadata(
                regionFormat: "MQ",
                deviceType: "iPhone31,5",
                platformArchitecture: "arm64e",
                osVersion: "iPhone OS 26.9 (33E84)",
                lowPowerModeEnabled: true
            ),
            applicationVersion: "1.0",
            timestamp: 1_700_000_000
        )

        let report = try self.loadOnlyReport()
        XCTAssertEqual(FBArch.arm64, report.deviceMetrics!.arch)
        XCTAssertEqual("iPhone31,5", report.deviceMetrics!.model!)
        XCTAssertEqual("26.9", report.deviceMetrics!.osBuild!.version!)
        XCTAssertEqual("33E84", report.deviceMetrics!.osBuild!.kernOsversion!)
        XCTAssertEqual("MQ", report.appMetrics!.regionFormat!)
        XCTAssert(report.deviceMetrics!.lowPowerModeEnabled)
    }

    func testWriteCrashReportUsesSdkIdentifierAndVersion() throws {
        self.writer = self.makeWriter(sdkVersion: "41.5.67")

        self.writer.writeCrashReport(
            with: .nativeCrash,
            dict: self.makeCrashDict(callStacks: []),
            name: nil,
            reason: nil,
            machExceptionType: nil,
            exceptionCode: nil,
            signal: nil,
            terminationReason: nil,
            capturedCrash: nil,
            metadata: self.makeMetadata(),
            applicationVersion: "1.0",
            timestamp: 1_700_000_000
        )

        let report = try self.loadOnlyReport()
        XCTAssertEqual("io.bitdrift.capture-apple", report.sdk!.id!)
        XCTAssertEqual("41.5.67", report.sdk!.version)
    }

    func testWriteNonCrashReportFilenameEncodesReportType() throws {
        self.writer.writeNonCrashReport(
            with: .appNotResponding,
            dict: self.makeCrashDict(callStacks: []),
            name: nil,
            reason: nil,
            order: .outerToInner,
            metadata: self.makeMetadata(),
            applicationVersion: "1.0",
            timestamp: 1_700_000_000
        )

        XCTAssertTrue(try self.onlyReportFilename().contains("_anr_"))
    }
}

private extension DiagnosticReportWriterTests {
    func makeWriter(
        sdkVersion: String = "9.9.9",
        fileSizeOptimizationEnabled: Bool = false,
        memoryPressureLevel: MemoryPressureLevel = .unknown
    ) -> DiagnosticReportWriter {
        DiagnosticReportWriter(
            outputDir: self.outputDir,
            sdkVersion: sdkVersion,
            fileSizeOptimizationEnabled: fileSizeOptimizationEnabled,
            memoryPressureLevel: memoryPressureLevel,
            fileManager: .default
        )
    }

    func makeThread(registers: [String: UInt64]? = nil) -> [String: Any] {
        var thread: [String: Any] = [
            "threadAttributed": true,
            "callStackRootFrames": [[
                "binaryUUID": "70B89F27-1634-3580-A695-57CDB41D7743",
                "offsetIntoBinaryTextSegment": UInt64(100),
                "binaryName": "TestBinary",
                "address": UInt64(7_170_766_264),
                "subFrames": [],
            ], ],
        ]

        if let registers {
            thread["bitdriftRegisters"] = registers
        }

        return thread
    }

    func registerPairs(of frame: FBFrame) -> [(String, UInt64)] {
        (0 ..< frame.registersCount).map { index in
            let register = frame.registers(at: index)!
            return (register.name!, register.value)
        }
    }

    func makeCrashDict(callStacks: [[String: Any]]) -> [String: Any] {
        ["callStackTree": ["callStacks": callStacks]]
    }

    func makeMetadata(
        bundleIdentifier: String = "com.example.app",
        appBuildVersion: String = "1",
        regionFormat: String = "US",
        deviceType: String = "iPhone17,2",
        platformArchitecture: String = "arm64e",
        osVersion: String = "iPhone OS 27.0 (24A5380h)",
        lowPowerModeEnabled: Bool = false
    ) -> [String: Any] {
        [
            "bundleIdentifier": bundleIdentifier,
            "appBuildVersion": appBuildVersion,
            "regionFormat": regionFormat,
            "deviceType": deviceType,
            "platformArchitecture": platformArchitecture,
            "osVersion": osVersion,
            "lowPowerModeEnabled": lowPowerModeEnabled,
        ]
    }

    func loadOnlyReport() throws -> FBReport {
        let files = try FileManager.default.contentsOfDirectory(atPath: self.outputDir.path)
        XCTAssertEqual(1, files.count)
        let contents = try XCTUnwrap(
            FileManager.default.contents(atPath: "\(self.outputDir.path)/\(files[0])"))
        var buf = ByteBuffer(data: contents)
        return try getCheckedRoot(byteBuffer: &buf)
    }

    func onlyReportFilename() throws -> String {
        let files = try FileManager.default.contentsOfDirectory(atPath: self.outputDir.path)
        XCTAssertEqual(1, files.count)
        return files[0]
    }
}
