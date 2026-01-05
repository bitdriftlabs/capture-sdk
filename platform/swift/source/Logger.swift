// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
internal import CapturePassable
import Foundation

public final class Logger {
    enum State {
        // The logger has not yet been started
        case notStarted
        // The logger has been successfully started and is ready for use.
        // Subsequent attempts to start the logger will be ignored.
        case started(LoggerIntegrator)
        // An attempt to start the logger was made but failed.
        // Subsequent attempts to start the logger will be ignored.
        case startFailure
    }

    /// A no-op implementation of SessionReplayTarget used when session replay is disabled.
    private final class NoopSessionReplayTarget: CaptureLoggerBridge.SessionReplayTarget {
        func captureScreen() {}
        func captureScreenshot() {}
    }

    private let underlyingLogger: CoreLogging
    private let timeProvider: TimeProvider

    private let remoteErrorReporter: RemoteErrorReporting
    private let deviceCodeController: DeviceCodeController

    private(set) var sessionReplayController: SessionReplayController?
    private(set) var dispatchSourceMemoryMonitor: DispatchSourceMemoryMonitor?
    private(set) var resourceUtilizationTarget: ResourceUtilizationController
    private(set) var eventsListenerTarget: EventSubscriber

    private let sessionURLBase: URL

    static var issueReporterInitResult: IssueReporterInitResult = (.notInitialized, 0)
    static var diagnosticReporter = Atomic<DiagnosticEventReporter?>(nil)

    private static let syncedShared = Atomic<State>(.notStarted)

    private let network: URLSessionNetworkClient?
    // Used for benchmarking purposes.
    var metrics: URLSessionNetworkClient.Metrics? {
        return self.network?.metrics
    }

    /// Instantiates an instance of the Logger with the passed in API key and a base URL for connecting to
    /// bitdrift Capture remote services.
    ///
    /// - parameter apiKey:                        The application key associated with your development
    ///                                            account. Provided by bitdrift.
    /// - parameter configuration:                 A configuration that specifies Capture features to enable.
    /// - parameter sessionStrategy:               The session strategy to use.
    /// - parameter dateProvider:                  The date provider to use, if any. The logger defaults to
    ///                                            system date provider if none is provided.
    /// - parameter fieldProviders:                The field providers to use when querying the list of
    ///                                            attributes to attach to emitted logs.
    /// - parameter loggerBridgingFactoryProvider: A class to use for Rust bridging. Used for testing
    ///                                            purposes.
    convenience init?(
        withAPIKey apiKey: String,
        configuration: Configuration,
        sessionStrategy: SessionStrategy,
        dateProvider: DateProvider?,
        fieldProviders: [FieldProvider],
        loggerBridgingFactoryProvider: LoggerBridgingFactoryProvider = LoggerBridgingFactory()
    )
    {
        self.init(
            withAPIKey: apiKey,
            remoteErrorReporter: nil,
            configuration: configuration,
            sessionStrategy: sessionStrategy,
            dateProvider: dateProvider,
            fieldProviders: fieldProviders,
            storageProvider: Storage.shared,
            timeProvider: SystemTimeProvider(),
            loggerBridgingFactoryProvider: loggerBridgingFactoryProvider
        )
    }

    // swiftlint:disable function_body_length
    /// Internal constructor shared between the public convenience initializers and tests. Generally
    /// production apps would not pass in a bufferDirectory, it would default to the Capture default.
    /// Instead, this should only be set in tests to ensure each test is run with a clean slate.
    ///
    /// - parameter apiKey:                        The application key associated with your development
    ///                                            account. Provided by bitdrift.
    /// - parameter remoteErrorReporter:           The error reporter to use, if any. Otherwise the logger
    ///                                            creates its own error reporter.
    /// - parameter configuration:                 A configuration that specifies Capture features to enable.
    /// - parameter sessionStrategy:               The session strategy to use.
    /// - parameter dateProvider:                  The date provider to use, if any. The logger defaults to
    ///                                            system date provider if none is provided.
    /// - parameter fieldProviders:                The field providers to use when querying the list of
    ///                                            attributes to attach to emitted logs.
    /// - parameter enableNetwork:                 Whether logger should perform network request. If not all
    ///                                            network requests performed by the logger are no-ops.
    /// - parameter storageProvider:               The storage to use by the logger.
    /// - parameter timeProvider:                  The time source to use by the logger.
    /// - parameter loggerBridgingFactoryProvider: A class to use for Rust bridging. Used for testing
    ///                                            purposes.
    init?(
        withAPIKey apiKey: String,
        remoteErrorReporter: RemoteErrorReporting?,
        configuration: Configuration,
        sessionStrategy: SessionStrategy,
        dateProvider: DateProvider?,
        fieldProviders: [FieldProvider],
        enableNetwork: Bool = true,
        storageProvider: StorageProvider,
        timeProvider: TimeProvider,
        loggerBridgingFactoryProvider: LoggerBridgingFactoryProvider = LoggerBridgingFactory()
    )
    {
        self.timeProvider = timeProvider
        let start = timeProvider.uptime()

        // Order of providers matters in here, the latter in the list the higher their priority in
        // case of key conflicts.
        let appStateAttributes = AppStateAttributes()
        let clientAttributes = ClientAttributes()
        let deviceAttributes = DeviceAttributes()
        let networkAttributes = NetworkAttributes()
        let ootbFieldProviders: [FieldProvider] = [
            appStateAttributes,
            clientAttributes,
            deviceAttributes,
            networkAttributes,
        ]

        let metadataProvider = MetadataProviderController(
            dateProvider: dateProvider ?? SystemDateProvider(),
            ootbFieldProviders: ootbFieldProviders,
            customFieldProviders: fieldProviders
        )

        self.sessionURLBase = Self.normalizedAPIURL(apiURL: configuration.apiURL)

        let client = APIClient(apiURL: configuration.apiURL, apiKey: apiKey)
        self.remoteErrorReporter = remoteErrorReporter
            ?? RemoteErrorReportingClient(
                client: client,
                fieldProviders: [appStateAttributes, clientAttributes]
            )

        guard let directoryURL = configuration.rootFileURL ?? Logger.captureSDKDirectory() else {
            return nil
        }

        let network: URLSessionNetworkClient? = enableNetwork
            ? URLSessionNetworkClient(apiBaseURL: configuration.apiURL)
            : nil
        self.network = network

        self.resourceUtilizationTarget = ResourceUtilizationController(
            storageProvider: storageProvider,
            timeProvider: timeProvider
        )
        self.eventsListenerTarget = EventSubscriber()

        self.sessionReplayController = configuration.sessionReplayConfiguration.map {
            SessionReplayController(configuration: $0)
        }

        guard let logger = loggerBridgingFactoryProvider.makeLogger(
            apiKey: apiKey,
            bufferDirectoryPath: directoryURL.path,
            sessionStrategy: sessionStrategy,
            metadataProvider: metadataProvider,
            // TODO(Augustyniak): Pass `resourceUtilizationTarget`, `sessionReplayTarget`,
            // and `eventsListenerTarget` as part of the `self.underlyingLogger.start()` method call instead.
            // Pass the event listener target here and finish setting up
            // before the logger is actually started.
            resourceUtilizationTarget: self.resourceUtilizationTarget,
            sessionReplayTarget: (self.sessionReplayController ?? NoopSessionReplayTarget()),
            // Pass the event listener target here and finish setting up
            // before the logger is actually started.
            eventsListenerTarget: self.eventsListenerTarget,
            appID: clientAttributes.appID,
            releaseVersion: clientAttributes.appVersion,
            buildNumber: clientAttributes.buildNumber,
            osVersion: clientAttributes.osVersion,
            osBrand: "Apple",
            model: deviceAttributes.hardwareVersion,
            network: network,
            errorReporting: self.remoteErrorReporter,
            sleepMode: configuration.sleepMode
        ) else {
            return nil
        }

        self.underlyingLogger = CoreLogger(logger: logger)

        defer {
            let duration = timeProvider.timeIntervalSince(start)
            let fields: Fields = [
                "_fatal_issue_reporting_state": "\(Logger.issueReporterInitResult.0)",
                "_fatal_issue_reporting_duration_ms": Logger.issueReporterInitResult.1 * Double(MSEC_PER_SEC),
                "_session_replay_enabled": (configuration.sessionReplayConfiguration != nil),
            ]
            self.underlyingLogger.logSDKStart(fields: fields, duration: duration)
        }

        self.eventsListenerTarget.setUp(
            logger: self.underlyingLogger,
            appStateAttributes: appStateAttributes,
            clientAttributes: clientAttributes,
            timeProvider: timeProvider
        )
        self.resourceUtilizationTarget.logger = self.underlyingLogger

        network?.logger = self.underlyingLogger
        metadataProvider.errorHandler = { [weak underlyingLogger] context, error in
            underlyingLogger?.handleError(context: context, error: error)
        }
        self.sessionReplayController?.logger = self.underlyingLogger

        // Start attributes before the underlying logger is running to increase the chances
        // of out-of-the-box attributes being ready by the time logs emitted as a result of the logger start
        // are emitted.
        deviceAttributes.start()
        networkAttributes.start(with: self.underlyingLogger)

        self.underlyingLogger.start()

        self.dispatchSourceMemoryMonitor = Self.setUpMemoryStateMonitoring(logger: self.underlyingLogger)

        self.deviceCodeController = DeviceCodeController(client: client)

        #if targetEnvironment(simulator)
        Logger.issueReporterInitResult = (.initialized(.unsupportedHardware), 0)
        #else
        if !configuration.enableFatalIssueReporting {
            Logger.issueReporterInitResult = (.initialized(.clientNotEnabled), 0)
        } else {
            Logger.issueReporterInitResult = measureTime {
                guard let contents = Logger.cachedReportConfigData(base: directoryURL) else {
                    return .initialized(.runtimeNotSet)
                }
                guard let runtimeConfig = readCachedValues(contents) else {
                    return .initialized(.runtimeInvalid)
                }
                guard let enabled = runtimeConfig[RuntimeVariable.crashReporting.name] as? Bool, enabled else {
                    return .initialized(.runtimeNotEnabled)
                }

                let kscrashReportDir = Logger.kscrashReportDirectory(base: directoryURL)
                do {
                    try BitdriftKSCrashWrapper.configure(withCrashReportDirectory: kscrashReportDir)
                    try BitdriftKSCrashWrapper.startCrashReporter()
                } catch {
                }

                let hangDuration = self.underlyingLogger.runtimeValue(.applicationANRReporterThresholdMs)
                let reporter = DiagnosticEventReporter(
                    outputDir: Logger.reportCollectionDirectory(base: directoryURL),
                    sdkVersion: capture_get_sdk_version(),
                    eventTypes: .crash,
                    minimumHangSeconds: Double(hangDuration) / Double(MSEC_PER_SEC)) { [weak self] in
                    self?.underlyingLogger.processIssueReports(reportProcessingSession: .previousRun)
                }
                Logger.diagnosticReporter.update { val in
                    val = reporter
                }
                MXMetricManager.shared.add(reporter)
                return .initialized(.monitoring)
            }
        }
        #endif
    }

    // swiftlint:enable function_body_length

    /// Enables blocking shutdown operation. In practice, it makes the receiver's deinit wait for the complete
    /// shutdown of the underlying logger.
    ///
    /// For tests/profiling purposes only.
    func enableBlockingShutdown() {
        self.underlyingLogger.enableBlockingShutdown()
    }

    /// Sets the operation mode of the logger, where activating sleep mode
    /// reduces activity to a minimal level
    ///
    /// - parameter mode: the mode to use
    public func setSleepMode(_ mode: SleepMode) {
        self.underlyingLogger.setSleepMode(mode)
    }

    deinit {
        self.stop()
    }

    func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        matchingFields: Fields? = nil,
        error: Error? = nil,
        type: LogType,
        blocking: Bool = false
    ) {
        self.underlyingLogger.log(
            level: level,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: fields,
            matchingFields: matchingFields,
            error: error,
            type: type,
            blocking: blocking
        )
    }

    // MARK: - Static

    static func createOnce(_ createLogger: () -> Logger?) -> LoggerIntegrator? {
        let state = self.syncedShared.update { state in
            guard case .notStarted = state else {
                return
            }

            if let createdLogger = createLogger() {
                state = .started(LoggerIntegrator(logger: createdLogger))
            } else {
                state = .startFailure
            }
        }

        return switch state {
        case .started(let logger):
            logger
        case .notStarted, .startFailure:
            nil
        }
    }

    /// Retrieves a shared instance of logger if one has been started.
    ///
    /// - returns: The shared instance of logger.
    static func getShared() -> Logging? {
        return switch Self.syncedShared.load() {
        case .notStarted:
            nil
        case .started(let integrator):
            integrator.logger
        case .startFailure:
            nil
        }
    }

    /// Internal for testing purposes only.
    ///
    /// - parameter logger: The logger to use.
    static func resetShared(logger: Logging? = nil) {
        Self.syncedShared.update { state in
            if let logger {
                state = .started(LoggerIntegrator(logger: logger))
            } else {
                state = .notStarted
            }
        }
    }

    // Returns the location to use for storing files related to the Capture SDK, including the disk persisted
    // ring buffers.
    static func captureSDKDirectory() -> URL? {
        return try? FileManager.default
            .url(
                for: .applicationSupportDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: false
            )
            .appendingPathComponent("bitdrift_capture")
    }

    static func reportConfigPath(base: URL) -> URL {
        return base.appendingPathComponent("reports/config.csv", isDirectory: false)
    }

    static func kscrashReportDirectory(base: URL) -> URL {
        return base.appendingPathComponent("reports/kscrash", isDirectory: true)
    }

    static func reportCollectionDirectory(base: URL) -> URL {
        return base.appendingPathComponent("reports/new", isDirectory: true)
    }

    // MARK: - Private

    private static func cachedReportConfigData(base: URL) -> String? {
        let configPath = Logger.reportConfigPath(base: base)
        guard let data = FileManager.default.contents(atPath: configPath.path),
              let contents = String(data: data, encoding: .utf8)
        else {
            return nil
        }
        return contents
    }

    private static func normalizedAPIURL(apiURL: URL) -> URL {
        // We can assume a properly formatted api url is being used, so we can follow the same pattern
        // making sure we only replace the first occurrence
        var components = URLComponents(url: apiURL, resolvingAgainstBaseURL: false)
        if let range = components?.host?.range(of: "api.") {
            let host = components?.host
            components?.host = host?.replacingCharacters(in: range, with: "timeline.")
        }

        var hostComponents = URLComponents()
        hostComponents.scheme = "https"
        hostComponents.host = components?.host
        hostComponents.queryItems = [URLQueryItem(name: "utm_source", value: "sdk")]

        // We fallback to original `apiURL` in here to make the method return a non-optional `URL`.
        // In theory we could force unwrap `hostComponents.url` since - according to the documentation and how
        // we use it - it should never return `nil` but just to be safe we fallback to the original `apiURL`
        // instead.
        return hostComponents.url ?? apiURL
    }

    private func stop() {
        self.dispatchSourceMemoryMonitor?.stop()

        self.dispatchSourceMemoryMonitor = nil

        BitdriftKSCrashWrapper.stopCrashReporter()
    }
}

extension Logger: Logging {
    public var sessionID: String {
        return self.underlyingLogger.getSessionID()
    }

    public var sessionURL: String {
        return self.sessionURLBase
            .appendingPathComponent("s")
            .appendingPathComponent(self.sessionID)
            .absoluteString
    }

    public func startNewSession() {
        self.underlyingLogger.startNewSession()
    }

    public var deviceID: String {
        return self.underlyingLogger.getDeviceID()
    }

    public func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        file: String?,
        line: Int?,
        function: String?,
        fields: Fields?,
        error: Error?
    ) {
        self.log(
            level: level,
            message: message(),
            file: file,
            line: line,
            function: function,
            fields: fields,
            error: error,
            type: .normal,
            blocking: false
        )
    }

    public func log(
        _ request: HTTPRequestInfo,
        file: String?,
        line: Int?,
        function: String?
    ) {
        self.log(
            level: .debug,
            message: "HTTPRequest",
            file: file,
            line: line,
            function: function,
            fields: request.toFields(),
            matchingFields: request.toMatchingFields(),
            error: nil,
            type: .span,
            blocking: false
        )
    }

    public func log(
        _ response: HTTPResponseInfo,
        file: String?,
        line: Int?,
        function: String?
    ) {
        self.log(
            level: .debug,
            message: "HTTPResponse",
            file: file,
            line: line,
            function: function,
            fields: response.toFields(),
            matchingFields: response.toMatchingFields(),
            error: nil,
            type: .span,
            blocking: false
        )
    }

    public func addField(withKey key: String, value: FieldValue) {
        do {
            let stringValue = try value.encodeToString()
            self.underlyingLogger.addField(withKey: key, value: stringValue)
        } catch let error {
            self.underlyingLogger.handleError(
                context: "addField: failed to encode field with \"\(key)\" key",
                error: error
            )
        }
    }

    public func removeField(withKey key: String) {
        self.underlyingLogger.removeField(withKey: key)
    }

    public func setFeatureFlagExposure(withName flag: String, variant: String) {
        self.underlyingLogger.setFeatureFlagExposure(withName: flag, variant: variant)
    }

    public func setFeatureFlagExposure(withName flag: String, variant: Bool) {
        // TODO(snowp): We should make the internal state store expose a way to set the bool directly
        self.underlyingLogger.setFeatureFlagExposure(withName: flag, variant: String(variant))
    }

    public func createTemporaryDeviceCode(completion: @escaping (Result<String, Error>) -> Void) {
        // Access the `deviceID` when it is needed for creating the device code, rather than
        // at Logger's initialization time. Accessing it later almost guarantees that the
        // `deviceID` has been read and cached on the Tokio run-loop, making it a relatively
        // cheap operation. This approach avoids the heavy operation of reading from `UserDefaults`.
        self.deviceCodeController.createTemporaryDeviceCode(deviceID: self.deviceID, completion: completion)
    }

    public func logAppLaunchTTI(_ duration: TimeInterval) {
        self.underlyingLogger.logAppLaunchTTI(duration)
    }

    public func logScreenView(screenName: String) {
        self.underlyingLogger.logScreenView(screenName: screenName)
    }

    public func startSpan(
        name: String,
        level: LogLevel,
        file: String?,
        line: Int?,
        function: String?,
        fields: Fields?,
        startTimeInterval: TimeInterval?,
        parentSpanID: UUID?
    ) -> Span
    {
        Span(
            logger: self.underlyingLogger,
            name: name,
            level: level,
            file: file,
            line: line,
            function: function,
            fields: fields,
            timeProvider: self.timeProvider,
            customStartTimeInterval: startTimeInterval,
            parentSpanID: parentSpanID
        )
    }

    public func startDebugOperationsAsNeeded() {
        if !DebugHeuristics.isDebugLikeEnvironment {
            return
        }

        self.deviceCodeController.createCodeOnDebugConsole(for: self.deviceID)
    }
}

// MARK: - Features

extension Logger {
    static func setUpMemoryStateMonitoring(
        logger: CoreLogging
    ) -> DispatchSourceMemoryMonitor
    {
        let dispatchSourceMemoryMonitor = DispatchSourceMemoryMonitor(logger: logger)
        dispatchSourceMemoryMonitor.start()
        return dispatchSourceMemoryMonitor
    }
}
