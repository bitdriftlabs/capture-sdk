// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

struct CrashReporterSetupResult {
    let initResult: IssueReporterInitResult
    let previousRunInfo: PreviousRunInfo
    let diagnosticReporter: DiagnosticEventReporter?
}

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
    private let environment: AppEnvironment
    private let previousRunInfoController: PreviousRunInfoController?

    private var isBitdriftCrashHandlerEnabled = false

    init(
        previousRunInfoController: PreviousRunInfoController?,
        ksCrashHandler: any KSCrashHandling = BitdriftKSCrashWrapper(),
        bitdriftCrashHandler: any BitdriftCrashHandling = BitdriftCrashHandler(),
        metricManager: MXMetricManager = .shared,
        fileManager: FileManager = .default,
        environment: AppEnvironment = LiveEnvironment()
    ) {
        self.ksCrashHandler = ksCrashHandler
        self.bitdriftCrashHandler = bitdriftCrashHandler
        self.metricManager = metricManager
        self.fileManager = fileManager
        self.environment = environment
        self.previousRunInfoController = previousRunInfoController
    }

    /// Initializes all crash handlers, resolves the previous-run status, and builds the
    /// `DiagnosticEventReporter`.
    ///
    /// - Warning: The returned result must be applied to shared state before activating
    /// MetricKit to avoid a race with payload delivery.
    ///
    /// - parameter sdkBaseURL:       the base URL under which crash reporting directories are located.
    /// - parameter underlyingLogger: the logger used to read runtime feature flags and build the
    ///                                diagnostic reporter.
    ///
    /// - returns: the combined result of crash handler initialization, previous-run info, and the
    ///            diagnostic reporter.
    func setup(sdkBaseURL: URL, underlyingLogger: CoreLogging) -> CrashReporterSetupResult {
        guard !environment.isSimulator else {
            return CrashReporterSetupResult(
                initResult: (.initialized(.unsupportedHardware), 0),
                previousRunInfo: .unknown,
                diagnosticReporter: nil
            )
        }

        var lastRunResult = false
        var diagnosticReporter: DiagnosticEventReporter?

        let initResult: IssueReporterInitResult = measureTime {
            isBitdriftCrashHandlerEnabled = underlyingLogger.runtimeValue(.bdCrashReporter)

            let runtimeState = resolveRuntimeState(from: sdkBaseURL)
            if runtimeState != .monitoring {
                return .initialized(runtimeState)
            }

            var reporterInitResolution: ReporterInitResolution?

            if case .failure = initializeKSCrash(atURL: sdkBaseURL) {
                reporterInitResolution = .degraded
            }

            if isBitdriftCrashHandlerEnabled {
                // TODO: see how to handle this failure.
                // e.g. log via underlyingLogger or extend ReporterInitResolution
                _ = initializeBitdriftCrashReporter(atURL: sdkBaseURL)
            }

            lastRunResult = didCrashLastLaunch()
            diagnosticReporter = makeDiagnosticReporter(sdkBaseURL: sdkBaseURL, underlyingLogger: underlyingLogger)

            return .initialized(reporterInitResolution ?? .monitoring)
        }

        let previousRunInfo: PreviousRunInfo
        if underlyingLogger.runtimeValue(.previousRunInfoRevamped) {
            self.previousRunInfoController?.resolve(didCrashLastLaunch: lastRunResult)
            previousRunInfo = self.previousRunInfoController?.previousRunInfo ?? .unknown
        } else {
            previousRunInfo = lastRunResult ? PreviousRunInfo(terminationReason: .fatalCrash) : .unknown
        }

        return CrashReporterSetupResult(
            initResult: initResult,
            previousRunInfo: previousRunInfo,
            diagnosticReporter: diagnosticReporter
        )
    }

    /// Registers the `DiagnosticEventReporter` with MetricKit.
    ///
    /// - Warning: Must be called after the result of `setup(sdkBaseURL:underlyingLogger:)`
    /// has been applied to shared state.
    ///
    /// - parameter reporter: the diagnostic reporter to register with MetricKit.
    func activate(reporter: DiagnosticEventReporter) {
        self.metricManager.add(reporter)
    }

    /// Stops all active crash handlers. Called on logger teardown.
    func stop() {
        self.ksCrashHandler.stopCrashReporter()
        if isBitdriftCrashHandlerEnabled {
            self.bitdriftCrashHandler.stopCrashReporter()
        }
    }
}

// MARK: - CrashReporting

extension CrashReporterService: CrashReporting  {
    // bd-crash-reporter captures the exact crash time; fall back to KSCrash if unavailable.
    func cachedCrashDate() -> Date? {
        guard isBitdriftCrashHandlerEnabled else {
            return self.ksCrashHandler.cachedCrashDate()
        }
        return self.bitdriftCrashHandler.cachedCrashDate() ?? self.ksCrashHandler.cachedCrashDate()
    }

    func cachedPreviousCrash() -> BitdriftPreviousCrash? {
        isBitdriftCrashHandlerEnabled ? self.bitdriftCrashHandler.cachedPreviousCrash() : nil
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

    func didCrashLastLaunch() -> Bool {
        if let crashed = self.ksCrashHandler.didCrashLastLaunch() {
            return crashed.boolValue
        }

        if isBitdriftCrashHandlerEnabled {
            return self.bitdriftCrashHandler.didCrashLastLaunch()?.boolValue == true
        }

        return false
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
        let configPath = baseURL.appendingPathComponent(
            Constants.configCSV,
            isDirectory: false
        )
        guard let data = self.fileManager.contents(atPath: configPath.path),
              let contents = String(data: data, encoding: .utf8)
        else {
            // For initial app installation/clear cache, the configuration wasn't written
            // to disk yet, so we intentionally enable crash reporting to not miss any
            // of those early crashes.
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
