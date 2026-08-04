// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation
import MetricKit

/// Abstracts away that a conformer likely relies on OS-version-gated APIs (e.g. `MetricManager`,
/// iOS 27+), so callers can hold and stop one without a preproc/compiler check
protocol MetricKitDiagnosticManaging: AnyObject {
    func start()
    func stop()
}

#if compiler(>=6.4)

@available(iOS 27.0, *)
protocol MetricDiagnosticReportSource {
    var diagnosticReports: any AsyncSequence<DiagnosticReport, Never> { get }
}

@available(iOS 27.0, *)
struct LiveMetricDiagnosticReportSource: MetricDiagnosticReportSource {
    let metricManager: MetricManager

    var diagnosticReports: any AsyncSequence<DiagnosticReport, Never> {
        self.metricManager.diagnosticReports
    }
}

@available(iOS 27.0, *)
final class MetricKitDiagnosticManager: MetricKitDiagnosticManaging {
    private enum Constants {
        static let defaultHangName = "App Hang"
        static let defaultOOMExceptionName = "EXC_RESOURCE"
        static let defaultOOMDescription = "Out Of Memory"
    }

    private let outputDir: URL
    private let useStackOverlapMatching: Bool
    private let crashReporting: any CrashReporting
    private let parsing: MetricKitDiagnosticParsing
    private let crashEnrichmentSummaryHandler: (([String: String]?) -> Void)?
    private let completionHandler: (() -> Void)?
    private let diagnosticSource: any MetricDiagnosticReportSource
    private let fileManager: FileManager
    private let writer: MetricKitReportWriter

    private var task: Task<Void, Never>?

    init(
        outputDir: URL,
        sdkVersion: String,
        memoryPressureLevel: MemoryPressureLevel,
        fileSizeOptimizationEnabled: Bool,
        useStackOverlapMatching: Bool,
        crashReporting: any CrashReporting,
        parsing: MetricKitDiagnosticParsing = MetricKitDiagnosticParsing(),
        diagnosticSource: any MetricDiagnosticReportSource = LiveMetricDiagnosticReportSource(metricManager: MetricManager()),
        fileManager: FileManager = .default,
        crashEnrichmentSummaryHandler: (([String: String]?) -> Void)? = nil,
        completionHandler: (() -> Void)? = nil
    ) {
        self.outputDir = outputDir
        self.useStackOverlapMatching = useStackOverlapMatching
        self.crashReporting = crashReporting
        self.parsing = parsing
        self.diagnosticSource = diagnosticSource
        self.fileManager = fileManager
        self.crashEnrichmentSummaryHandler = crashEnrichmentSummaryHandler
        self.completionHandler = completionHandler
        self.writer = MetricKitReportWriter(
            outputDir: outputDir,
            sdkVersion: sdkVersion,
            fileSizeOptimizationEnabled: fileSizeOptimizationEnabled,
            memoryPressureLevel: memoryPressureLevel,
            fileManager: fileManager
        )
    }

    func start() {
        self.task = Task.detached { [weak self] in
            guard let self else {
                return
            }

            for await report in self.diagnosticSource.diagnosticReports {
                self.handle(report)
            }
        }
    }

    func stop() {
        self.task?.cancel()
        self.task = nil
    }

    // MARK: - Dispatch

    private func handle(_ report: DiagnosticReport) {
        guard self.ensureOutputDirectoryExists() else {
            return
        }

        let timestamp = report.timeRange.end.timeIntervalSince1970

        switch report.result {
        case let .crash(diagnostic):
            let capturedCrash = self.crashReporting.cachedPreviousCrash()
            self.processCrash(
                diagnostic,
                environment: report.environment,
                timestamp: timestamp,
                capturedCrash: capturedCrash
            )
        case let .memoryException(diagnostic):
            self.processMemoryException(diagnostic, environment: report.environment, timestamp: timestamp)
        default:
            // Everything else (.hang, .cpuException, .diskWriteException, .appLaunch) isn't
            // captured here; hangs are categorized as watchdog SIGKILLs in processCrash instead.
            break
        }

        self.completionHandler?()
    }

    private func ensureOutputDirectoryExists() -> Bool {
        if self.fileManager.fileExists(atPath: self.outputDir.path) {
            return true
        }
        
        do {
            try self.fileManager.createDirectory(
                at: self.outputDir,
                withIntermediateDirectories: true
            )
        } catch {
            return false
        }
        
        return true
    }

    // MARK: - Crash

    private func processCrash(
        _ diagnostic: CrashDiagnostic,
        environment: DiagnosticReport.Environment,
        timestamp: TimeInterval,
        capturedCrash: BitdriftPreviousCrash?
    ) {
        // The new MetricKit API exposes watchdog terminations directly via `terminationCategory`,
        // unlike the old API where this had to be inferred from exceptionType/signal/terminationReason
        // heuristics (see DiagnosticEventReporter.crashIsHangTermination:). Prefer the clean signal.
        let isHang = diagnostic.terminationCategory == .watchdog
        let effectiveCapturedCrash = isHang ? nil : capturedCrash
        let reportType: ReportType = isHang ? .appNotResponding : .nativeCrash

        let name = isHang ? Constants.defaultHangName : self.name(forCrash: diagnostic)
        let reason = self.reason(forCrash: diagnostic, name: name, capturedCrash: effectiveCapturedCrash)

        let adapterDict = MetricKitLegacyCrashAdapter.makeCrashDict(diagnostic: diagnostic, environment: environment)
        var summaryOut: NSDictionary?
        let enhanced = self.crashReporting.enhancedMetricKitReport(
            adapterDict,
            useStackOverlapMatching: self.useStackOverlapMatching,
            summaryOut: &summaryOut
        )
        if let summary = summaryOut as? [String: String] {
            self.crashEnrichmentSummaryHandler?(summary)
        }

        let threads = ExtractedThread.create(fromThreadsDictionary: enhanced).map { $0.makeReportThread() }

        self.writer.writeCrashReport(
            with: reportType,
            threads: threads,
            name: name,
            reason: reason,
            machExceptionType: diagnostic.exceptionType.map { NSNumber(value: $0) },
            exceptionCode: diagnostic.exceptionCode.map { NSNumber(value: $0) },
            signal: diagnostic.signal.map { NSNumber(value: $0) },
            terminationReason: diagnostic.terminationReason?.rawValue,
            capturedCrash: effectiveCapturedCrash,
            environment: environment.asMetricKitReportEnvironment(),
            timestamp: timestamp
        )
    }

    // MARK: - Memory exception (new; no equivalent in the old MetricKit API)

    private func processMemoryException(
        _ diagnostic: MemoryExceptionDiagnostic,
        environment: DiagnosticReport.Environment,
        timestamp: TimeInterval
    ) {
        let threads = ExtractedThread.create(fromCallStackTree: diagnostic.callStackTree).map { $0.makeReportThread() }

        self.writer.writeMemoryExceptionReport(
            with: threads,
            name: Constants.defaultOOMExceptionName,
            reason: Constants.defaultOOMDescription,
            environment: environment.asMetricKitReportEnvironment(),
            timestamp: timestamp
        )
    }

    // MARK: - Crash naming

    private func name(forCrash diagnostic: CrashDiagnostic) -> String? {
        self.parsing.name(forExceptionType: Int32(diagnostic.exceptionType ?? 0))
            ?? self.parsing.name(forSignal: Int32(diagnostic.signal ?? 0))
    }

    private func reason(
        forCrash diagnostic: CrashDiagnostic,
        name: String?,
        capturedCrash: BitdriftPreviousCrash?
    ) -> String? {
        var components: [String] = []
        var hasMetricKitExceptionReason = false

        if let exceptionReason = diagnostic.exceptionReason {
            hasMetricKitExceptionReason = true
            components.append("\(exceptionReason.exceptionName): \(exceptionReason.composedMessage)")
        }

        if !hasMetricKitExceptionReason,
           capturedCrash?.kind == .nsException,
           let nsexception = capturedCrash?.nsexception,
           let reason = nsexception.reason {
            components.append("\(nsexception.name): \(reason)")
        }

        if let terminationReason = diagnostic.terminationReason?.rawValue, !terminationReason.isEmpty {
            components.append(terminationReason)
        }

        if let vmRegionInfo = diagnostic.virtualMemoryRegionInfo {
            components.append(vmRegionInfo)
        }

        if !components.isEmpty {
            return components.joined(separator: ".\n")
        }

        let signalName = self.parsing.name(forSignal: Int32(diagnostic.signal ?? 0))
        if let exceptionCode = diagnostic.exceptionCode {
            return "code: \(exceptionCode), signal: \(signalName ?? "unknown")"
        }

        let reason = signalName ?? "unknown"
        return reason == name ? nil : reason
    }
}

@available(iOS 27.0, *)
private extension DiagnosticReport.Environment {
    func asMetricKitReportEnvironment() -> MetricKitReportEnvironment {
        MetricKitReportEnvironment(
            bundleIdentifier: self.bundleIdentifier,
            applicationVersion: self.applicationVersion,
            applicationBuildVersion: self.applicationBuildVersion,
            regionFormat: self.regionFormat,
            deviceType: self.deviceType,
            osVersionName: self.osVersion.platform,
            osVersionNumber: self.osVersion.number,
            osVersionBuildNumber: self.osVersion.buildNumber,
            platformArchitecture: self.platformArchitecture,
            lowPowerModeEnabled: self.lowPowerModeEnabled
        )
    }
}

#endif
