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

typealias Report = bitdrift_public_fbs_issue_reporting_v1_Report
typealias ReportType = bitdrift_public_fbs_issue_reporting_v1_ReportType

final class DiagnosticEventReporterTests: XCTestCase {
    private var reportDir: URL?
    private var dateFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }

    override func setUp() async throws {
        reportDir = try createTempDir()
    }

    override func tearDown() async throws {
        if let dir = self.reportDir {
            try FileManager.default.removeItem(at: dir)
        }
    }

    private func getTestBundleFileUrl(_ name: String) -> URL {
        let testBundle = Bundle(for: type(of: self))
        let url = testBundle.url(forResource: name, withExtension: nil)!
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        return url
    }

    private func getTestBundleFileUrl(_ name: String, subdir: String) -> URL {
        let testBundle = Bundle(for: type(of: self))
        let url = testBundle.url(forResource: name, withExtension: nil, subdirectory: subdir)!
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        return url
    }

    private func getContentsOfTestBundleJsonFile(_ name: String) throws -> [String: any Equatable] {
        return try JSONSerialization.jsonObject(
            with: Data(contentsOf: getTestBundleFileUrl(name)),
            options: []
        ) as! Dictionary
    }

    private func dictsAreEqual(_ dict1: [String: any Equatable], _ dict2: [String: any Equatable]) -> Bool {
        guard dict1.keys == dict2.keys else {
            return false
        }
        for key in dict1.keys {
            let value1 = dict1[key]!
            let value2 = dict2[key]!

            if let value1AsDict = value1 as? [String: any Equatable],
               let value2AsDict = value2 as? [String: any Equatable]
            {
                if !dictsAreEqual(value1AsDict, value2AsDict) {
                    return false
                }
            } else if "\(value1)" != "\(value2)" {
                return false
            }
        }
        return true
    }

    func testReportMerging() throws {
        let metrickitReport = try! getContentsOfTestBundleJsonFile("metrickit-example.json")
        XCTAssertNotNil(metrickitReport)
        let kscrashPath = getTestBundleFileUrl("kscrash-example.bjn")
        XCTAssertTrue(FileManager.default.fileExists(atPath: kscrashPath.path))

        XCTAssertTrue(BitdriftKSCrashHandler.configure(withCrashReportFilePath: kscrashPath))
        let enhancedReport = BitdriftKSCrashHandler.enhancedMetricKitReport(metrickitReport) as! [String: any Equatable]
        XCTAssertNotNil(enhancedReport)
        XCTAssertFalse(dictsAreEqual(metrickitReport, enhancedReport))

        let expectedNames = ["com.apple.uikit.eventfetch-thread",
                             "tokio-runtime-worker",
                             "io.bitdrift.capture.anr-reporter",
                             "tokio-runtime-worker",
                             "io.bitdrift.capture.buffer.Verbose Buffer",
                             "io.bitdrift.capture.buffer.Continuous Buffer",
                             "com.apple.NSURLConnectionLoader",
                             // This won't be found because its stack trace wasn't captured
                             // "KSCrash Exception Handler (Primary)",
        ]
        let callStackTree = enhancedReport["callStackTree"] as! [String: Any]
        let callStacks = callStackTree["callStacks"] as! [Dictionary<String, any Equatable>]
        var foundNames: [String] = []
        for stack in callStacks {
            if let name = stack["name"] as? String {
                foundNames.append(name)
            }
        }
        XCTAssertEqual(expectedNames.sorted(), foundNames.sorted())
    }

    func testSdkAttributes() throws {
        let reporter = DiagnosticEventReporter(outputDir: reportDir!, sdkVersion: "41.5.67", eventTypes: .crash, minimumHangSeconds: 2.5)
        let crash = try MockCrashDiagnostic(signal: 9, exceptionType: nil, exceptionCode: nil, callStacks: [])
        reporter.didReceive([MockDiagnosticPayload(crashDiagnostics: [crash])])

        let path = reportDir!.path
        let files = try FileManager.default.contentsOfDirectory(atPath: path)
        XCTAssertEqual(1, files.count)
        XCTAssertTrue(files[0].contains("_crash_"))

        let contents = FileManager.default.contents(atPath: "\(path)/\(files[0])")!
        var buf = ByteBuffer(data: contents)
        let report: Report = try! getCheckedRoot(byteBuffer: &buf)
        XCTAssertEqual("io.bitdrift.capture-apple", report.sdk!.id!)
        XCTAssertEqual("41.5.67", report.sdk!.version)
        XCTAssertEqual(ReportType.nativecrash, report.type)
    }

    func testDeviceTime() throws {
        let reporter = DiagnosticEventReporter(outputDir: reportDir!, sdkVersion: "41.5.67", eventTypes: .crash, minimumHangSeconds: 2.5)
        let crash = try MockCrashDiagnostic(signal: 9, exceptionType: nil, exceptionCode: nil, callStacks: [])
        let timestamp = dateFormatter.date(from: "1995-03-11")!
        reporter.didReceive([MockDiagnosticPayload(crashDiagnostics: [crash], timestamp: timestamp)])

        let path = reportDir!.path
        let files = try FileManager.default.contentsOfDirectory(atPath: path)
        XCTAssertEqual(1, files.count)
        XCTAssertTrue(files[0].contains("_crash_"))

        let contents = FileManager.default.contents(atPath: "\(path)/\(files[0])")!
        var buf = ByteBuffer(data: contents)
        let report: Report = try! getCheckedRoot(byteBuffer: &buf)
        let device = report.deviceMetrics!
        XCTAssertEqual(UInt64(timestamp.timeIntervalSince1970), device.time!.seconds)
    }

    func testMachExceptionNameInError() throws {
        let reporter = DiagnosticEventReporter(outputDir: reportDir!, sdkVersion: "41.5.67", eventTypes: .crash, minimumHangSeconds: 2.5)
        let crash = try MockCrashDiagnostic(signal: 4, exceptionType: 1)
        reporter.didReceive([MockDiagnosticPayload(crashDiagnostics: [crash])])

        let path = reportDir!.path
        let files = try FileManager.default.contentsOfDirectory(atPath: path)
        XCTAssertEqual(1, files.count)
        XCTAssertTrue(files[0].contains("_crash_"))

        let contents = FileManager.default.contents(atPath: "\(path)/\(files[0])")!
        var buf = ByteBuffer(data: contents)
        let report: Report = try! getCheckedRoot(byteBuffer: &buf)
        XCTAssertEqual(1, report.errorsCount)

        let error = report.errors(at: 0)!
        XCTAssertEqual("EXC_BAD_ACCESS", error.name!)
        XCTAssertEqual("SIGILL", error.reason!)
    }

    @available(iOS 17.0, *)
    func testNSExceptionNameInError() throws {
        let reporter = DiagnosticEventReporter(outputDir: reportDir!, sdkVersion: "41.5.67", eventTypes: .crash, minimumHangSeconds: 2.5)
        let crashReason = MockObjectiveCReason("NSRangeException", message: "index 5 out of range [0..2]")
        let crash = try MockCrashDiagnostic(signal: 4, exceptionType: 1, exceptionReason: crashReason)

        reporter.didReceive([MockDiagnosticPayload(crashDiagnostics: [crash])])

        let path = reportDir!.path
        let files = try FileManager.default.contentsOfDirectory(atPath: path)
        XCTAssertEqual(1, files.count)
        XCTAssertTrue(files[0].contains("_crash_"))

        let contents = FileManager.default.contents(atPath: "\(path)/\(files[0])")!
        var buf = ByteBuffer(data: contents)
        let report: Report = try! getCheckedRoot(byteBuffer: &buf)
        XCTAssertEqual(1, report.errorsCount)

        let error = report.errors(at: 0)!
        XCTAssertEqual("EXC_BAD_ACCESS", error.name!)
        XCTAssertEqual("NSRangeException: index 5 out of range [0..2]", error.reason!)
    }

    func testWatchdogTermination() throws {
        let termContext = """
<RBSTerminateContext| domain:10 code:0x8BADF00D explanation:[app<com.example.myapp>:123] Failed to terminate gracefully after 5.0s
ProcessVisibility: Foreground
ProcessState: Running
WatchdogEvent: process-exit
WatchdogVisibility: Background
WatchdogCPUStatistics: (
"Elapsed total CPU time (seconds): 9.680 (user 8.060, system 1.620), 30% CPU",
"Elapsed application CPU time (seconds): 5.035, 17% CPU"
)
ThermalInfo: (
"Thermal Level:   11",
"Thermal State:   critical"
) reportType:CrashLog maxTerminationResistance:Interactive>
"""
        let reporter = DiagnosticEventReporter(outputDir: reportDir!, sdkVersion: "41.5.67", eventTypes: .crash, minimumHangSeconds: 2.5)
        let crash = try MockCrashDiagnostic(signal: 9, exceptionType: 10, exceptionCode: 0, terminationReason: termContext)
        reporter.didReceive([MockDiagnosticPayload(crashDiagnostics: [crash])])

        let path = reportDir!.path
        let files = try FileManager.default.contentsOfDirectory(atPath: path)
        XCTAssertEqual(1, files.count)
        XCTAssertTrue(files[0].contains("_anr_"))

        let contents = FileManager.default.contents(atPath: "\(path)/\(files[0])")!
        var buf = ByteBuffer(data: contents)
        let report: Report = try! getCheckedRoot(byteBuffer: &buf)

        let error = report.errors(at: 0)!
        XCTAssertEqual("App Hang", error.name!)
        XCTAssertEqual(termContext, error.reason!)
    }

    func testSendAttributedEmptyStack() throws {
        let reporter = DiagnosticEventReporter(outputDir: reportDir!, sdkVersion: "41.5.67", eventTypes: .crash, minimumHangSeconds: 2.5)
        let imageOffset: UInt64 = 165304
        let imageOffset2: UInt64 = 126721
        let crash = try MockCrashDiagnostic(signal: 9, callStacks: [
            ["threadAttributed": true, "callStackRootFrames": []],
            ["callStackRootFrames": [["binaryUUID": "70B89F27-1634-3580-A695-57CDB41D7743",
                                      "offsetIntoBinaryTextSegment": imageOffset,
                                      "sampleCount": 1,
                                      "binaryName": "MetricKitTestApp",
                                      "address": 7170766264,
                                      "subFrames": [["binaryUUID":
                                                        "D366A690-4127-4BB6-B6A9-019A2ACD0D8D",
                                                     "offsetIntoBinaryTextSegment": imageOffset2,
                                                     "sampleCount": 1,
                                                     "binaryName": "Proc",
                                                     "address": 23786237891,
                                      ], ],
            ], ], ],
        ])
        reporter.didReceive([MockDiagnosticPayload(crashDiagnostics: [crash])])

        let path = reportDir!.path
        let files = try FileManager.default.contentsOfDirectory(atPath: path)
        XCTAssertEqual(1, files.count)

        let contents = FileManager.default.contents(atPath: "\(path)/\(files[0])")!
        var buf = ByteBuffer(data: contents)
        let report: Report = try! getCheckedRoot(byteBuffer: &buf)
        XCTAssertNil(report.threadDetails)

        let error = report.errors(at: 0)!
        XCTAssertEqual("SIGKILL", error.name!)
        XCTAssertEqual(2, error.stackTraceCount)

        // frame order is the opposite of hangs (FB18377370)
        let frame = error.stackTrace(at: 0)!
        XCTAssertEqual("70B89F27-1634-3580-A695-57CDB41D7743", frame.imageId)
        XCTAssertEqual(7170766264, frame.frameAddress)

        let frame2 = error.stackTrace(at: 1)!
        XCTAssertEqual("D366A690-4127-4BB6-B6A9-019A2ACD0D8D", frame2.imageId)
        XCTAssertEqual(23786237891, frame2.frameAddress)

        XCTAssertEqual(2, report.binaryImagesCount)

        let image = report.binaryImages(at: 0)!
        XCTAssertEqual("MetricKitTestApp", image.path!)
        XCTAssertEqual(frame.frameAddress - imageOffset, image.loadAddress)

        let image2 = report.binaryImages(at: 1)!
        XCTAssertEqual("Proc", image2.path!)
        XCTAssertEqual(frame2.frameAddress - imageOffset2, image2.loadAddress)
    }

    func testDiscardInvalidFrame() throws {
        let reporter = DiagnosticEventReporter(outputDir: reportDir!, sdkVersion: "41.5.67", eventTypes: .crash, minimumHangSeconds: 2.5)
        let imageOffset: UInt64 = 165304
        let imageOffset2: UInt64 = 126721
        let crash = try MockCrashDiagnostic(signal: 6, callStacks: [
            ["callStackRootFrames": [
                ["binaryUUID": "70B89F27-1634-3580-A695-57CDB41D7743",
                 "offsetIntoBinaryTextSegment": imageOffset,
                 "sampleCount": 1,
                 "binaryName": "MetricKitTestApp",
                 "address": 7170766264,
                 "subFrames": [
                    // invalid frame, no address nor binary info:
                    ["subFrames": [["binaryUUID":
                                        "D366A690-4127-4BB6-B6A9-019A2ACD0D8D",
                                    "offsetIntoBinaryTextSegment": imageOffset2,
                                    "sampleCount": 1,
                                    "binaryName": "Proc",
                                    "address": 23786237891, ],
                    ],
                    ],
                 ],
                ],
            ],
            ],
        ])
        reporter.didReceive([MockDiagnosticPayload(crashDiagnostics: [crash])])

        let path = reportDir!.path
        let files = try FileManager.default.contentsOfDirectory(atPath: path)
        XCTAssertEqual(1, files.count)

        let contents = FileManager.default.contents(atPath: "\(path)/\(files[0])")!
        var buf = ByteBuffer(data: contents)
        let report: Report = try! getCheckedRoot(byteBuffer: &buf)
        XCTAssertNil(report.threadDetails)

        let error = report.errors(at: 0)!
        XCTAssertEqual("SIGABRT", error.name!)
        XCTAssertEqual(1, error.stackTraceCount)

        let frame = error.stackTrace(at: 0)!
        XCTAssertEqual("70B89F27-1634-3580-A695-57CDB41D7743", frame.imageId)
        XCTAssertEqual(7170766264, frame.frameAddress)

        XCTAssertEqual(1, report.binaryImagesCount)

        let image = report.binaryImages(at: 0)!
        XCTAssertEqual("MetricKitTestApp", image.path!)
        XCTAssertEqual(frame.frameAddress - imageOffset, image.loadAddress)
    }

    func testHangUnderThreshold() throws {
        let reporter = DiagnosticEventReporter(outputDir: reportDir!, sdkVersion: "41.5.67", eventTypes: [.hang, .crash], minimumHangSeconds: 1)
        let timestamp = dateFormatter.date(from: "2022-04-07")!
        let hang = try MockHangDiagnostic(duration: 0.7, type: "Main Runloop Hang", callStacks: [
            ["threadAttributed": true, "callStackRootFrames": [["binaryUUID":
                                                                    "70B89F27-1634-3580-A695-57CDB41D7743",
                                                                "offsetIntoBinaryTextSegment": 165304,
                                                                "sampleCount": 1,
                                                                "binaryName": "MetricKitTestApp",
                                                                "address": 7170766264,
            ], ], ],
        ])
        reporter.didReceive([MockDiagnosticPayload(hangDiagnostics: [hang], timestamp: timestamp)])

        let path = reportDir!.path
        let files = try FileManager.default.contentsOfDirectory(atPath: path)
        XCTAssertEqual(0, files.count)
    }

    func testHangOverThreshold() throws {
        let reporter = DiagnosticEventReporter(outputDir: reportDir!, sdkVersion: "41.5.67", eventTypes: [.hang, .crash], minimumHangSeconds: 1)
        let timestamp = dateFormatter.date(from: "2008-11-25")!
        let imageOffset: UInt64 = 165304
        let imageOffset2: UInt64 = 126721
        let hang = try MockHangDiagnostic(duration: 1.7, type: "Main Runloop Hang", callStacks: [
            ["threadAttributed": true, "callStackRootFrames": [["binaryUUID":
                                                                    "70B89F27-1634-3580-A695-57CDB41D7743",
                                                                "offsetIntoBinaryTextSegment": imageOffset,
                                                                "sampleCount": 1,
                                                                "binaryName": "MetricKitTestApp",
                                                                "address": 7170766264,
                                                                "subFrames": [["binaryUUID":
                                                                                "D366A690-4127-4BB6-B6A9-019A2ACD0D8D",
                                                                               "offsetIntoBinaryTextSegment": imageOffset2,
                                                                               "sampleCount": 1,
                                                                               "binaryName": "Proc",
                                                                               "address": 23786237891,
                                                                ], ],
            ],
            ], ],
            ["callStackRootFrames": [["binaryUUID": "70B89F27-1634-3580-A695-57CDB41D7743",
                                      "offsetIntoBinaryTextSegment": 100415,
                                      "sampleCount": 1,
                                      "binaryName": "MetricKitTestApp",
                                      "address": 7170701375,
            ], ], ],
        ])
        reporter.didReceive([MockDiagnosticPayload(hangDiagnostics: [hang], timestamp: timestamp)])

        let path = reportDir!.path
        let files = try FileManager.default.contentsOfDirectory(atPath: path)
        XCTAssertEqual(1, files.count)
        XCTAssertTrue(files[0].contains("_anr_"))

        let contents = FileManager.default.contents(atPath: "\(path)/\(files[0])")!
        var buf = ByteBuffer(data: contents)
        let report: Report = try! getCheckedRoot(byteBuffer: &buf)
        XCTAssertEqual(1, report.threadDetails!.threadsCount) // # of items in threads array
        XCTAssertEqual(2, report.threadDetails!.count) // total system threads
        XCTAssertEqual(ReportType.appnotresponding, report.type)

        let thread = report.threadDetails!.threads(at: 0)!
        XCTAssertEqual(1, thread.stackTraceCount)

        var frame = thread.stackTrace(at: 0)!
        XCTAssertEqual("70B89F27-1634-3580-A695-57CDB41D7743", frame.imageId)
        XCTAssertEqual(7170701375, frame.frameAddress)

        let error = report.errors(at: 0)!
        XCTAssertEqual("Main Runloop Hang", error.name!)
        let reason = error.reason?.replacingOccurrences(of: "1,7", with: "1.7")
        XCTAssertEqual("app was unresponsive for 1.7 sec", reason!)
        XCTAssertEqual(2, error.stackTraceCount)

        // frame order is the opposite of crashes (FB18377370)
        frame = error.stackTrace(at: 0)!
        XCTAssertEqual("D366A690-4127-4BB6-B6A9-019A2ACD0D8D", frame.imageId)
        XCTAssertEqual(23786237891, frame.frameAddress)

        let frame2 = error.stackTrace(at: 1)!
        XCTAssertEqual("70B89F27-1634-3580-A695-57CDB41D7743", frame2.imageId)
        XCTAssertEqual(7170766264, frame2.frameAddress)

        XCTAssertEqual(2, report.binaryImagesCount)

        let image = report.binaryImages(at: 0)!
        XCTAssertEqual("MetricKitTestApp", image.path!)
        XCTAssertEqual("70B89F27-1634-3580-A695-57CDB41D7743", image.id!)
        XCTAssertEqual(frame2.frameAddress - imageOffset, image.loadAddress)

        let image2 = report.binaryImages(at: 1)!
        XCTAssertEqual("Proc", image2.path!)
        XCTAssertEqual("D366A690-4127-4BB6-B6A9-019A2ACD0D8D", image2.id!)
        XCTAssertEqual(frame.frameAddress - imageOffset2, image2.loadAddress)
    }

    func testManyThreadCrashReport() throws {
        let reporter = DiagnosticEventReporter(outputDir: reportDir!, sdkVersion: "41.5.67", eventTypes: [.crash], minimumHangSeconds: 1)
        let timestamp = dateFormatter.date(from: "2008-11-25")!
        let imageOffset: UInt64 = 165304
        let imageOffset2: UInt64 = 126721
        let imageOffset3: UInt64 = 100415
        let crash = try MockCrashDiagnostic(signal: 5, callStacks: [
            ["callStackRootFrames": [["binaryUUID": "E41D0413-92D1-4B00-9B62-43D57A1B0CC5",
                                      "offsetIntoBinaryTextSegment": imageOffset3,
                                      "sampleCount": 1,
                                      "binaryName": "Camp",
                                      "address": 7170701375,
            ], ], ],
            ["threadAttributed": true, "callStackRootFrames": [["binaryUUID":
                                                                    "70B89F27-1634-3580-A695-57CDB41D7743",
                                                                "offsetIntoBinaryTextSegment": imageOffset,
                                                                "sampleCount": 1,
                                                                "binaryName": "MyApp",
                                                                "address": 7170766264,
                                                                "subFrames": [["binaryUUID":
                                                                                "D366A690-4127-4BB6-B6A9-019A2ACD0D8D",
                                                                               "offsetIntoBinaryTextSegment": imageOffset2,
                                                                               "sampleCount": 1,
                                                                               "binaryName": "Proc",
                                                                               "address": 23786237891,
                                                                ], ],
            ],
            ], ],
            ["callStackRootFrames": [["binaryUUID": "70B89F27-1634-3580-A695-57CDB41D7743",
                                      "offsetIntoBinaryTextSegment": 100415,
                                      "sampleCount": 1,
                                      "binaryName": "MyApp",
                                      "address": 9283737662,
                                      "subFrames": [["binaryUUID":
                                                        "D366A690-4127-4BB6-B6A9-019A2ACD0D8D",
                                                     "offsetIntoBinaryTextSegment": imageOffset2,
                                                     "sampleCount": 1,
                                                     "binaryName": "Proc",
                                                     "address": 23786237891,
                                      ], ],
            ], ], ],
        ])
        reporter.didReceive([MockDiagnosticPayload(crashDiagnostics: [crash], timestamp: timestamp)])

        let path = reportDir!.path
        let files = try FileManager.default.contentsOfDirectory(atPath: path)
        XCTAssertEqual(1, files.count)
        XCTAssertTrue(files[0].contains("_crash_"))

        let contents = FileManager.default.contents(atPath: "\(path)/\(files[0])")!
        var buf = ByteBuffer(data: contents)
        let report: Report = try! getCheckedRoot(byteBuffer: &buf)
        XCTAssertEqual(2, report.threadDetails!.threadsCount) // # of items in threads array
        XCTAssertEqual(3, report.threadDetails!.count) // total system threads
        XCTAssertEqual(ReportType.nativecrash, report.type)

        var thread = report.threadDetails!.threads(at: 0)!
        XCTAssertEqual(1, thread.stackTraceCount)
        XCTAssertEqual(0, thread.index)

        var frame = thread.stackTrace(at: 0)!
        XCTAssertEqual("E41D0413-92D1-4B00-9B62-43D57A1B0CC5", frame.imageId)
        XCTAssertEqual(7170701375, frame.frameAddress)

        thread = report.threadDetails!.threads(at: 1)!
        XCTAssertEqual(2, thread.stackTraceCount)
        XCTAssertEqual(2, thread.index) // error is thread 1

        frame = thread.stackTrace(at: 0)!
        XCTAssertEqual("70B89F27-1634-3580-A695-57CDB41D7743", frame.imageId)
        XCTAssertEqual(9283737662, frame.frameAddress)

        frame = thread.stackTrace(at: 1)!
        XCTAssertEqual("D366A690-4127-4BB6-B6A9-019A2ACD0D8D", frame.imageId)
        XCTAssertEqual(23786237891, frame.frameAddress)

        let error = report.errors(at: 0)!
        XCTAssertEqual("SIGTRAP", error.name!)
        XCTAssertEqual(2, error.stackTraceCount)

        // frame order is the opposite of crashes (FB18377370)
        frame = error.stackTrace(at: 0)!
        XCTAssertEqual("70B89F27-1634-3580-A695-57CDB41D7743", frame.imageId)
        XCTAssertEqual(7170766264, frame.frameAddress)

        let frame2 = error.stackTrace(at: 1)!
        XCTAssertEqual("D366A690-4127-4BB6-B6A9-019A2ACD0D8D", frame2.imageId)
        XCTAssertEqual(23786237891, frame2.frameAddress)

        XCTAssertEqual(3, report.binaryImagesCount)

        let image0 = report.binaryImages(at: 0)!
        XCTAssertEqual("Camp", image0.path!)
        XCTAssertEqual("E41D0413-92D1-4B00-9B62-43D57A1B0CC5", image0.id!)
        XCTAssertEqual(7170701375 - imageOffset3, image0.loadAddress)

        let image1 = report.binaryImages(at: 1)!
        XCTAssertEqual("MyApp", image1.path!)
        XCTAssertEqual("70B89F27-1634-3580-A695-57CDB41D7743", image1.id!)
        XCTAssertEqual(frame.frameAddress - imageOffset, image1.loadAddress)

        let image2 = report.binaryImages(at: 2)!
        XCTAssertEqual("Proc", image2.path!)
        XCTAssertEqual("D366A690-4127-4BB6-B6A9-019A2ACD0D8D", image2.id!)
        XCTAssertEqual(frame2.frameAddress - imageOffset2, image2.loadAddress)
    }
}

// MARK: - utility functions

func createTempDir() throws -> URL {
    let tempDir = NSURL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true).appendingPathComponent(UUID().uuidString)!
    try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    return tempDir
}

// MARK: - mock classes

final class MockDiagnosticPayload: MXDiagnosticPayload {
    let mockCrashDiagnostics: [MXCrashDiagnostic]?
    let mockHangDiagnostics: [MXHangDiagnostic]?
    let mockTimestampEnd: Date

    override var crashDiagnostics: [MXCrashDiagnostic]? { get { return mockCrashDiagnostics } }
    override var hangDiagnostics: [MXHangDiagnostic]? { get { return mockHangDiagnostics } }
    override var timeStampEnd: Date { get { return mockTimestampEnd } }

    init(crashDiagnostics: [MXCrashDiagnostic]? = nil,
         hangDiagnostics: [MXHangDiagnostic]? = nil,
         timestamp: Date = Date()
    ) {
        self.mockCrashDiagnostics = crashDiagnostics
        self.mockHangDiagnostics = hangDiagnostics
        self.mockTimestampEnd = timestamp
        super.init()
    }

    required init?(coder: NSCoder) {
        self.mockCrashDiagnostics = nil
        self.mockHangDiagnostics = nil
        self.mockTimestampEnd = Date()
        super.init(coder: coder)
    }
}

final class MockCrashDiagnostic: MXCrashDiagnostic {
    let mockCallStackTree: MockCallStackTree?
    let mockSignal: NSNumber?
    let mockExceptionType: NSNumber?
    let mockExceptionCode: NSNumber?
    let mockExceptionReason: Any?
    let mockTerminationReason: String?

    override var callStackTree: MXCallStackTree { get { return mockCallStackTree! } }
    override var signal: NSNumber? { get { return mockSignal } }
    override var exceptionType: NSNumber? { get { return mockExceptionType  } }
    override var exceptionCode: NSNumber? { get { return mockExceptionCode } }
    override var terminationReason: String? { get { return mockTerminationReason } }

    @available(iOS 17.0, *)
    override var exceptionReason: MXCrashDiagnosticObjectiveCExceptionReason? {
        get {
            if let reason = mockExceptionReason {
                return reason as! MockObjectiveCReason
            }
            return nil
        }
    }

    override func dictionaryRepresentation() -> [AnyHashable: Any] {
        return ["callStackTree": self.mockCallStackTree?.stacks ?? [:]]
    }

    init(signal: NSNumber? = nil,
         exceptionType: NSNumber? = nil,
         exceptionCode: NSNumber? = nil,
         terminationReason: String? = nil,
         callStacks: [[String: Any]] = []
    ) throws {
        self.mockCallStackTree = try MockCallStackTree(callStacks)
        self.mockSignal = signal
        self.mockExceptionType = exceptionType
        self.mockExceptionCode = exceptionCode
        self.mockExceptionReason = nil
        self.mockTerminationReason = terminationReason
        super.init()
    }

    @available(iOS 17.0, *)
    init(signal: NSNumber? = nil,
         exceptionType: NSNumber? = nil,
         exceptionCode: NSNumber? = nil,
         exceptionReason: MockObjectiveCReason? = nil,
         terminationReason: String? = nil,
         callStacks: [[String: Any]] = []
    ) throws {
        self.mockCallStackTree = try MockCallStackTree(callStacks)
        self.mockSignal = signal
        self.mockExceptionType = exceptionType
        self.mockExceptionCode = exceptionCode
        self.mockExceptionReason = exceptionReason
        self.mockTerminationReason = terminationReason
        super.init()
    }

    required init?(coder: NSCoder) {
        self.mockCallStackTree = nil
        self.mockSignal = nil
        self.mockExceptionCode = nil
        self.mockExceptionType = nil
        self.mockExceptionReason = nil
        self.mockTerminationReason = nil
        super.init(coder: coder)
    }
}

final class MockCallStackTree: MXCallStackTree {
    let stacks: [String: Any]
    let data: Data
    init(_ stacks: [[String: Any]]) throws {
        self.stacks = ["callStacksPerThread": true, "callStacks": stacks]
        let treeData = try JSONSerialization.data(withJSONObject: self.stacks)
        self.data = treeData
        super.init()
    }

    required init?(coder: NSCoder) {
        self.stacks = [:]
        self.data = Data()
        super.init(coder: coder)
    }

    override func jsonRepresentation() -> Data {
        return self.data
    }
}

@available(iOS 17.0, *)
final class MockObjectiveCReason: MXCrashDiagnosticObjectiveCExceptionReason {
    let mockName: String
    let mockMessage: String
    override var exceptionName: String { get { return mockName } }
    override var composedMessage: String { get { return mockMessage } }

    init(_ exceptionName: String, message: String) {
        mockName = exceptionName
        mockMessage = message
        super.init()
    }

    required init?(coder: NSCoder) {
        mockName = ""
        mockMessage = ""
        super.init(coder: coder)
    }
}

final class MockHangDiagnostic: MXHangDiagnostic {
    let mockDuration: TimeInterval
    let mockCallStackTree: MockCallStackTree?
    let mockType: String?

    override var callStackTree: MXCallStackTree { get { return mockCallStackTree! } }
    override var hangDuration: Measurement<UnitDuration> {
        get {
            return Measurement(value: self.mockDuration, unit: .seconds)
        }
    }

    override func dictionaryRepresentation() -> [AnyHashable: Any] {
        return [
            "callStackTree": self.mockCallStackTree?.stacks ?? [:],
            "diagnosticMetaData": ["hangDuration": mockDuration],
            "hangType": mockType ?? "",
        ]
    }

    init(duration: TimeInterval,
         type: String? = nil,
         callStacks: [[String: Any]] = []) throws {
        mockDuration = duration
        mockCallStackTree = try MockCallStackTree(callStacks)
        mockType = type
        super.init()
    }

    required init?(coder: NSCoder) {
        mockDuration = 0
        mockCallStackTree = nil
        mockType = ""
        super.init(coder: coder)
    }
}
