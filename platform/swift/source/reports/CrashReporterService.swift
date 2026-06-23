// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

/// Coordinates crash reporter initialization and exposes the combined state of all active handlers
/// through the `CrashReporting` protocol, abstracting away which reporters are in use.
@objc class CrashReporterService: NSObject {
    private enum Constants {
        static let KSCrashDirectory = "reports/kscrash"
        static let bitdriftCrashReporterDirectory = "reports/bitdrift_crash_reporter"
        static let configCSV = "reports/config.csv"
        static let reportCollectionDirectory = "reports/new"
    }

    private let ksCrashHandler: any KSCrashHandling
    private let bitdriftCrashHandler: any BitdriftCrashHandling
    private let metricManager: MXMetricManager
    private let fileManager: FileManager

    init(
        ksCrashHandler: any KSCrashHandling = BitdriftKSCrashWrapper(),
        bitdriftCrashHandler: any BitdriftCrashHandling = BitdriftCrashHandler(),
        metricManager: MXMetricManager = .shared,
        fileManager: FileManager = .default
    ) {
        self.ksCrashHandler = ksCrashHandler
        self.bitdriftCrashHandler = bitdriftCrashHandler
        self.metricManager = metricManager
        self.fileManager = fileManager
    }

    func setup(sdkBaseURL: URL, underlyingLogger: CoreLogging) {
        #if targetEnvironment(simulator)
        Logger.hasFatallyTerminatedOnPreviousRun = nil
        Logger.issueReporterInitResult = (.initialized(.unsupportedHardware), 0)
        #else
        Logger.issueReporterInitResult = measureTime {
            let runtimeState = resolveRuntimeState(from: sdkBaseURL)
            if runtimeState != .monitoring {
                return .initialized(runtimeState)
            }
            _ = initializeKSCrash(atURL: sdkBaseURL)
            _ = initializeBitdriftCrashReporter(atURL: sdkBaseURL)
            Logger.hasFatallyTerminatedOnPreviousRun = didCrashLastLaunch()

            let reporter = makeDiagnosticReporter(sdkBaseURL: sdkBaseURL, underlyingLogger: underlyingLogger)
            Logger.diagnosticReporter.update { val in val = reporter }
            self.metricManager.add(reporter)
            return .initialized(.monitoring)
        }
        #endif
    }

    func stop() {
        self.ksCrashHandler.stopCrashReporter()
        self.bitdriftCrashHandler.stopCrashReporter()
    }
}

// MARK: - CrashReporting

extension CrashReporterService: CrashReporting  {
    // bd-crash-reporter captures the exact crash time; fall back to KSCrash if unavailable.
    func cachedCrashDate() -> Date? {
        self.bitdriftCrashHandler.cachedPreviousCrash()?.crashDate ?? self.ksCrashHandler.cachedCrashDate()
    }

    func cachedPreviousCrash() -> BitdriftPreviousCrash? {
        self.bitdriftCrashHandler.cachedPreviousCrash()
    }

    func enhancedMetricKitReport(
        _ metricKitReport: [String: Any],
        useStackOverlapMatching: Bool,
        summaryOut: AutoreleasingUnsafeMutablePointer<NSDictionary?>?
    ) -> [String: Any] {
        self.ksCrashHandler.enhancedMetricKitReport(
            metricKitReport,
            useStackOverlapMatching: useStackOverlapMatching,
            summaryOut: summaryOut
        )
    }
}

private extension CrashReporterService {
    func initializeKSCrash(atURL url: URL) -> Result<Void, Error> {
        let directoryURL = url.appendingPathComponent(
            Constants.KSCrashDirectory,
            isDirectory: true
        )

        do {
            try self.ksCrashHandler.configure(withCrashReportDirectory: directoryURL)
            try self.ksCrashHandler.startCrashReporter()
        } catch {
            return .failure(error)
        }

        return .success(())
    }

    func initializeBitdriftCrashReporter(atURL url: URL) -> Result<Void, Error> {
        let directoryURL = url.appendingPathComponent(
            Constants.bitdriftCrashReporterDirectory,
            isDirectory: true
        )

        do {
            try self.bitdriftCrashHandler.configure(withCrashReportDirectory: directoryURL)
            try self.bitdriftCrashHandler.startCrashReporter()
        } catch {
            return .failure(error)
        }

        return .success(())
    }

    func didCrashLastLaunch() -> Bool? {
        if let crashed = self.ksCrashHandler.didCrashLastLaunch() {
            return crashed.boolValue
        }
        return self.bitdriftCrashHandler.didCrashLastLaunch()?.boolValue
    }

    func makeDiagnosticReporter(sdkBaseURL: URL, underlyingLogger: CoreLogging) -> DiagnosticEventReporter {
        let hangDuration = underlyingLogger.runtimeValue(.applicationANRReporterThresholdMs)
        let useStackOverlapMatching = underlyingLogger.runtimeValue(.crashThreadMatchingByStackOverlap)
        let memoryPressureLevel = underlyingLogger.previousMemoryPressureLevel()
        let outputDir = sdkBaseURL.appendingPathComponent(Constants.reportCollectionDirectory, isDirectory: true)
        return DiagnosticEventReporter(
            outputDir: outputDir,
            sdkVersion: capture_get_sdk_version(),
            eventTypes: .crash,
            minimumHangSeconds: Double(hangDuration) / Double(MSEC_PER_SEC),
            memoryPressureLevel: memoryPressureLevel,
            useStackOverlapMatching: useStackOverlapMatching,
            crashReporting: self,
            crashEnrichmentSummaryHandler: { [weak underlyingLogger] summary in
                let matcherMode = useStackOverlapMatching ? "base" : "exact"
                guard let underlyingLogger,
                      let summary,
                      let outcome = summary["outcome"],
                      let reason = summary["reason"]
                else {
                    return
                }
                underlyingLogger.logInternal(
                    level: .debug,
                    message: "[CrashEnrichment] MetricKit crash enrichment summary",
                    fields: [
                        "outcome": outcome,
                        "reason": reason,
                        "matcher_mode": matcherMode,
                    ]
                )
            }
        ) { [weak underlyingLogger] in
            underlyingLogger?.processIssueReports(reportProcessingSession: .previousRun)
        }
    }

    func resolveRuntimeState(from baseURL: URL) -> ReporterInitResolution {
        let configPath = baseURL.appendingPathComponent(Constants.configCSV, isDirectory: false)
        guard let data = self.fileManager.contents(atPath: configPath.path),
              let contents = String(data: data, encoding: .utf8)
        else {
            return .monitoring
        }

        guard let config = readCachedValues(contents) else {
            return .runtimeInvalid
        }

        switch config[RuntimeVariable.crashReporting.name] {
        case let enabled as Bool:
            return enabled ? .monitoring : .runtimeNotEnabled
        case nil:
            return .runtimeMissingFlag
        default:
            return .runtimeInvalid
        }
    }
}
