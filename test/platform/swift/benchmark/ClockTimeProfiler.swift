// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Benchmark
@testable import Capture
import CapturePassable
import CaptureTestBridge
import Foundation
import XCTest

// swiftlint:disable:next force_unwrapping
private let kAPIURL = URL(string: "https://api-tests.bitdrift.io")!
private let kDefaultSessionStrategy = SessionStrategy.fixed()
private let kLogMessage = "50 characters long test message - 0123456789012345"

// Time measurements for various logger operations such as configurations or logging.
final class ClockTimeProfiler {
    private(set) var runner: BenchmarkRunner?
    let suites: [BenchmarkSuite]

    init() {
        let configurationSuite = BenchmarkSuite(name: "Configuration")
        configurationSuite.register(benchmark: LoggerColdConfigurationBenchmark())
        configurationSuite.register(benchmark: LoggerWarmConfigurationBenchmark())

        self.suites = [
            configurationSuite,
            kPreConfigLogBenchmark,
            kPostConfigLogBenchmark,
        ]
    }

    func run() {
        self.runner = BenchmarkRunner(
            suites: self.suites,
            settings: [],
            customDefaults: defaultSettings
        )
        try? self.runner?.run()
    }
}

// Intended to simulate a configuration of a Logger that was preceded by
// at least one other configuration of the logger on a given device.
final class LoggerWarmConfigurationBenchmark: AnyBenchmark {
    private var logger: Logger?

    // MARK: AnyBenchmark

    let name = "[Reported] Warm Configuration"
    let settings: [Benchmark.BenchmarkSetting] = [WarmupIterations(10), Iterations(100)]

    func setUp() {}

    func run(_: inout Benchmark.BenchmarkState) throws {
        self.logger = Logger(
            withAPIKey: "foo",
            remoteErrorReporter: nil,
            configuration: .init(apiURL: kAPIURL),
            sessionStrategy: kDefaultSessionStrategy,
            dateProvider: nil,
            fieldProviders: [],
            enableNetwork: false,
            storageProvider: Storage.shared,
            timeProvider: SystemTimeProvider()
        )
    }

    func tearDown() {
        self.logger?.enableBlockingShutdown()
        self.logger = nil
    }
}

// Intended to simulate the first configuration of a Logger on a
// given device.
final class LoggerColdConfigurationBenchmark: AnyBenchmark {
    private var logger: Logger?

    // MARK: AnyBenchmark

    let name = "Cold Configuration"
    let settings: [Benchmark.BenchmarkSetting] = [WarmupIterations(10), Iterations(100)]

    func setUp() {
        Storage.shared.clear()
        if let url = Logger.captureSDKDirectory() {
            try? FileManager.default.removeItem(at: url)
        }
    }

    func run(_: inout Benchmark.BenchmarkState) throws {
        self.logger = Logger(
            withAPIKey: "foo",
            remoteErrorReporter: nil,
            configuration: .init(apiURL: kAPIURL),
            sessionStrategy: kDefaultSessionStrategy,
            dateProvider: nil,
            fieldProviders: [],
            enableNetwork: false,
            storageProvider: Storage.shared,
            timeProvider: SystemTimeProvider()
        )
        self.logger?.log(level: .info, message: kLogMessage, fields: [:], error: nil)
    }

    func tearDown() {
        self.logger?.enableBlockingShutdown()
        self.logger = nil
    }
}

public let kPreConfigLogBenchmark = BenchmarkSuite(name: "Logging - Pre-Config") { suite in
    guard let logger = try? Logger.make() else {
        return assertionFailure("failed to create a logger")
    }

    suite.benchmark("No fields", settings: [WarmupIterations(10), Iterations(120)]) {
        logger.log(
            level: .info,
            message: kLogMessage,
            file: nil,
            line: nil,
            function: nil,
            fields: [:],
            error: nil
        )
    }

    guard let logger5Fields = try? Logger.make() else {
        return assertionFailure("failed to create a logger")
    }

    suite.benchmark("5 fields", settings: [WarmupIterations(10), Iterations(120)]) {
        logger5Fields.log(
            level: .info,
            message: kLogMessage,
            file: nil,
            line: nil,
            function: nil,
            fields: [
                "keykeykey0": "valvalval0",
                "keykeykey2": "valvalval1",
                "keykeykey3": "valvalval2",
                "keykeykey4": "valvalval3",
                "keykeykey5": "valvalval4",
            ],
            error: nil
        )
    }

    guard let logger10Fields = try? Logger.make() else {
        return assertionFailure("failed to create a logger")
    }

    suite.benchmark("10 fields", settings: [WarmupIterations(10), Iterations(120)]) {
        logger10Fields.log(
            level: .info,
            message: kLogMessage,
            file: nil,
            line: nil,
            function: nil,
            fields: [
                "keykeykey0": "valvalval0",
                "keykeykey1": "valvalval1",
                "keykeykey2": "valvalval2",
                "keykeykey3": "valvalval3",
                "keykeykey4": "valvalval4",
                "keykeykey5": "valvalval5",
                "keykeykey6": "valvalval6",
                "keykeykey7": "valvalval7",
                "keykeykey8": "valvalval8",
                "keykeykey9": "valvalval9",
            ],
            error: nil
        )
    }

    guard let loggerOverflow = try? Logger.make() else {
        return assertionFailure("failed to create a logger")
    }

    suite.benchmark(
        "No fields, potential buffer overflow",
        settings: [WarmupIterations(10), Iterations(30_000)]
    ) {
        loggerOverflow.log(level: .info, message: kLogMessage, fields: [:], error: nil)
    }
}

private let kPostConfigLogBenchmark = BenchmarkSuite(name: "Logging - Post-Config") { suite in
    func makeLogger() -> Logger {
        guard let url = try? makeTmpDirectory() else {
            fatalError("failed to create a tmp directory")
        }

        create_benchmarking_configuration(url.path)
        guard let logger = try? Logger.make(directoryURL: url) else {
            fatalError("failed to create logger")
        }

        return logger
    }

    let logger = makeLogger()
    suite.benchmark("[Reported] log without fields", settings: [WarmupIterations(10), Iterations(120)]) {
        logger.log(
            level: .info,
            message: kLogMessage,
            file: nil,
            line: nil,
            function: nil,
            fields: nil,
            error: nil
        )
    }

    let logger5Fields = makeLogger()
    suite.benchmark("[Reported] log with 5 fields", settings: [WarmupIterations(10), Iterations(120)]) {
        logger5Fields.log(
            level: .info,
            message: kLogMessage,
            file: nil,
            line: nil,
            function: nil,
            fields: [
                "keykeykey0": "valvalval0",
                "keykeykey2": "valvalval1",
                "keykeykey3": "valvalval2",
                "keykeykey4": "valvalval3",
                "keykeykey5": "valvalval4",
            ],
            error: nil
        )
    }

    let logger10Fields = makeLogger()
    suite.benchmark("[Reported] log with 10 fields", settings: [WarmupIterations(10), Iterations(120)]) {
        logger10Fields.log(
            level: .info,
            message: kLogMessage,
            file: nil,
            line: nil,
            function: nil,
            fields: [
                "keykeykey0": "valvalval0",
                "keykeykey1": "valvalval1",
                "keykeykey2": "valvalval2",
                "keykeykey3": "valvalval3",
                "keykeykey4": "valvalval4",
                "keykeykey5": "valvalval5",
                "keykeykey6": "valvalval6",
                "keykeykey7": "valvalval7",
                "keykeykey8": "valvalval8",
                "keykeykey9": "valvalval9",
            ],
            error: nil
        )
    }

    let loggerOverflow = makeLogger()
    suite.benchmark("log without fields, potential buffer overflow",
                    settings: [WarmupIterations(10), Iterations(30_000)])
    {
        loggerOverflow.log(level: .info, message: kLogMessage, fields: [:], error: nil)
    }
}

private extension Logger {
    static func make(directoryURL: URL? = nil) throws -> Logger {
        let directoryURLFallback = try makeTmpDirectory()
        return try XCTUnwrap(Logger(
            withAPIKey: "foo",
            remoteErrorReporter: nil,
            configuration: .init(apiURL: kAPIURL, rootFileURL: directoryURL ?? directoryURLFallback),
            sessionStrategy: kDefaultSessionStrategy,
            dateProvider: nil,
            fieldProviders: [],
            enableNetwork: false,
            storageProvider: Storage.shared,
            timeProvider: SystemTimeProvider()
        )
        )
    }
}

private func makeTmpDirectory() throws -> URL {
    let fileManager = FileManager.default
    let directory = fileManager
        .temporaryDirectory
        .appendingPathComponent("capture", isDirectory: true)
        .appendingPathComponent(UUID().uuidString, isDirectory: true)

    // Create buffer directory for persisting ring buffers
    let exists = FileManager.default.fileExists(atPath: directory.path)
    do {
        if !exists {
            // Create the capture directory, Rust logger will create the buffer directory within.
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        }
    } catch let error {
        assertionFailure("failed to create a directory: \(error)")
    }

    return directory
}
