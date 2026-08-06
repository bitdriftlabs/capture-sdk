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
    private let writer: DiagnosticReportWriter

    private var task: Task<Void, Never>?

    init(
        outputDir: URL,
        sdkVersion: String,
        memoryPressureLevel: MemoryPressureLevel,
        fileSizeOptimizationEnabled: Bool,
        useStackOverlapMatching: Bool,
        crashReporting: any CrashReporting,
        parsing: MetricKitDiagnosticParsing = MetricKitDiagnosticParsing(),
        diagnosticSource: any MetricDiagnosticReportSource = LiveMetricDiagnosticReportSource(
            metricManager: MetricManager()),
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
        self.writer = DiagnosticReportWriter(
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
        case .crash(let diagnostic):
            let capturedCrash = self.crashReporting.cachedPreviousCrash()
            self.processCrash(
                diagnostic,
                environment: report.environment,
                timestamp: timestamp,
                capturedCrash: capturedCrash
            )
        case .memoryException(let diagnostic):
            self.processMemoryException(
                diagnostic, environment: report.environment, timestamp: timestamp)
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
        let isHang = self.parsing.isWatchdogHangTermination(
            withExceptionType: diagnostic.exceptionType.map { NSNumber(value: $0) },
            signal: diagnostic.signal.map { NSNumber(value: $0) },
            terminationReason: diagnostic.terminationReason?.rawValue,
            exceptionCode: diagnostic.exceptionCode.map { NSNumber(value: $0) }
        )
        let effectiveCapturedCrash = isHang ? nil : capturedCrash
        let reportType: ReportType = isHang ? .appNotResponding : .nativeCrash

        let name = isHang ? Constants.defaultHangName : self.name(forCrash: diagnostic)
        let reason = self.reason(
            forCrash: diagnostic, name: name, capturedCrash: effectiveCapturedCrash)

        let adapterDict = MetricKitLegacyCrashAdapter.makeCrashDict(
            diagnostic: diagnostic, environment: environment)
        var summaryOut: NSDictionary?
        let enhanced = self.crashReporting.enhancedMetricKitReport(
            adapterDict,
            useStackOverlapMatching: self.useStackOverlapMatching,
            summaryOut: &summaryOut
        )
        if let summary = summaryOut as? [String: String] {
            self.crashEnrichmentSummaryHandler?(summary)
        }

        self.writer.writeCrashReport(
            with: reportType,
            dict: enhanced,
            name: name,
            reason: reason,
            machExceptionType: diagnostic.exceptionType.map { NSNumber(value: $0) },
            exceptionCode: diagnostic.exceptionCode.map { NSNumber(value: $0) },
            signal: diagnostic.signal.map { NSNumber(value: $0) },
            terminationReason: diagnostic.terminationReason?.rawValue,
            capturedCrash: effectiveCapturedCrash,
            metadata: MetricKitLegacyCrashAdapter.makeMetadataDict(environment: environment),
            applicationVersion: environment.applicationVersion,
            timestamp: timestamp
        )
    }

    // MARK: - Memory exception (new; no equivalent in the old MetricKit API)

    private func processMemoryException(
        _ diagnostic: MemoryExceptionDiagnostic,
        environment: DiagnosticReport.Environment,
        timestamp: TimeInterval
    ) {
        self.writer.writeNonCrashReport(
            with: .nativeCrash,
            dict: MetricKitLegacyCrashAdapter.makeMemoryExceptionDict(diagnostic: diagnostic),
            name: Constants.defaultOOMExceptionName,
            reason: Constants.defaultOOMDescription,
            order: .outerToInner,
            metadata: MetricKitLegacyCrashAdapter.makeMetadataDict(environment: environment),
            applicationVersion: environment.applicationVersion,
            timestamp: timestamp
        )
    }

    // MARK: - Crash naming

    private func name(forCrash diagnostic: CrashDiagnostic) -> String? {
        self.parsing.name(
            forExceptionType: Int32(diagnostic.exceptionType ?? 0),
            signal: Int32(diagnostic.signal ?? 0)
        )
    }

    private func reason(
        forCrash diagnostic: CrashDiagnostic,
        name: String?,
        capturedCrash: BitdriftPreviousCrash?
    ) -> String? {
        var capturedCrashName: String?
        var capturedCrashReason: String?
        if capturedCrash?.kind == .nsException, let nsexception = capturedCrash?.nsexception,
           let reason = nsexception.reason
        {
            capturedCrashName = nsexception.name
            capturedCrashReason = reason
        }

        return self.parsing.reasonForCrash(
            withName: name,
            metricKitReasonName: diagnostic.exceptionReason?.exceptionName,
            metricKitReasonComposedMessage: diagnostic.exceptionReason?.composedMessage,
            capturedCrashName: capturedCrashName,
            capturedCrashReason: capturedCrashReason,
            terminationReason: diagnostic.terminationReason?.rawValue,
            virtualMemoryRegionInfo: diagnostic.virtualMemoryRegionInfo,
            exceptionCode: diagnostic.exceptionCode.map { NSNumber(value: $0) },
            signal: Int32(diagnostic.signal ?? 0)
        )
    }
}

#endif
