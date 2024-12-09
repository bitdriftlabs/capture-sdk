// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge
@_implementationOnly import CapturePassable
import Foundation

// swiftlint:disable file_length
public final class Logger {
    enum State {
        // The logger has not yet been started.
        case notStarted
        // The logger has been successfully started and is ready for use.
        // Subsequent attempts to start the logger will be ignored.
        case started(LoggerIntegrator)
        // An attempt to start the logger was made but failed.
        // Subsequent attempts to start the logger will be ignored.
        case startFailure
    }

    private let underlyingLogger: CoreLogging
    private let timeProvider: TimeProvider

    private let remoteErrorReporter: RemoteErrorReporting
    private let deviceCodeController: DeviceCodeController

    private(set) var sessionReplayTarget: SessionReplayTarget
    private(set) var dispatchSourceMemoryMonitor: DispatchSourceMemoryMonitor?
    private(set) var resourceUtilizationTarget: ResourceUtilizationTarget
    private(set) var eventsListenerTarget: EventsListenerTarget

    private let sessionURLBase: URL

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
    /// - parameter apiURL:                        The base URL of Capture API.
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
        apiURL: URL,
        configuration: Configuration,
        sessionStrategy: SessionStrategy,
        dateProvider: DateProvider?,
        fieldProviders: [FieldProvider],
        loggerBridgingFactoryProvider: LoggerBridgingFactoryProvider = LoggerBridgingFactory()
    )
    {
        self.init(
            withAPIKey: apiKey,
            bufferDirectory: nil,
            apiURL: apiURL,
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
    /// - parameter bufferDirectory:               The directory to use for storing files.
    /// - parameter apiURL:                        The base URL of Capture API.
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
        bufferDirectory: URL?,
        apiURL: URL,
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

        let metadataProvider = MetadataProvider(
            dateProvider: dateProvider ?? SystemDateProvider(),
            ootbFieldProviders: ootbFieldProviders,
            customFieldProviders: fieldProviders
        )

        self.sessionURLBase = Self.normalizedAPIURL(apiURL: apiURL)

        let client = APIClient(apiURL: apiURL, apiKey: apiKey)
        self.remoteErrorReporter = remoteErrorReporter
            ?? RemoteErrorReportingClient(
                client: client,
                fieldProviders: [appStateAttributes, clientAttributes]
            )

        let directoryURL = bufferDirectory ?? Logger.captureSDKDirectory()

        let network: URLSessionNetworkClient? = enableNetwork
            ? URLSessionNetworkClient(apiBaseURL: apiURL)
            : nil
        self.network = network

        self.resourceUtilizationTarget = ResourceUtilizationTarget(
            storageProvider: storageProvider,
            timeProvider: timeProvider
        )
        self.eventsListenerTarget = EventsListenerTarget()

        let sessionReplayTarget = SessionReplayTarget(configuration: configuration.sessionReplayConfiguration)
        self.sessionReplayTarget = sessionReplayTarget

        guard let logger = loggerBridgingFactoryProvider.makeLogger(
            apiKey: apiKey,
            bufferDirectoryPath: directoryURL?.path,
            sessionStrategy: sessionStrategy,
            metadataProvider: metadataProvider,
            // TODO(Augustyniak): Pass `resourceUtilizationTarget`, `sessionReplayTarget`,
            // and `eventsListenerTarget` as part of the `self.underlyingLogger.start()` method call instead.
            // Pass the event listener target here and finish setting up
            // before the logger is actually started.
            resourceUtilizationTarget: self.resourceUtilizationTarget,
            sessionReplayTarget: self.sessionReplayTarget,
            // Pass the event listener target here and finish setting up
            // before the logger is actually started.
            eventsListenerTarget: self.eventsListenerTarget,
            appID: clientAttributes.appID,
            releaseVersion: clientAttributes.appVersion,
            model: deviceAttributes.hardwareVersion,
            network: network,
            errorReporting: self.remoteErrorReporter
        ) else {
            return nil
        }

        self.underlyingLogger = CoreLogger(logger: logger)

        defer {
            let duration = timeProvider.timeIntervalSince(start)
            self.underlyingLogger.logSDKStart(fields: [:], duration: duration)
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
        self.sessionReplayTarget.logger = self.underlyingLogger

        // Start attributes before the underlying logger is running to increase the chances
        // of out-of-the-box attributes being ready by the time logs emitted as a result of the logger start
        // are emitted.
        deviceAttributes.start()
        networkAttributes.start(with: self.underlyingLogger)

        self.underlyingLogger.start()

        self.dispatchSourceMemoryMonitor = Self.setUpMemoryStateMonitoring(logger: self.underlyingLogger)

        self.deviceCodeController = DeviceCodeController(client: client)
    }

    // swiftlint:enable function_body_length

    /// Enables blocking shutdown operation. In practice, it makes the receiver's deinit wait for the complete
    /// shutdown of the underlying logger.
    ///
    /// For tests/profiling purposes only.
    func enableBlockingShutdown() {
        self.underlyingLogger.enableBlockingShutdown()
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
                for: .documentDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: false
            )
            .appendingPathComponent("bitdrift_capture")
    }

    // MARK: - Private

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

    public func startSpan(
        name: String,
        level: LogLevel,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields?
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
            timeProvider: self.timeProvider
        )
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
