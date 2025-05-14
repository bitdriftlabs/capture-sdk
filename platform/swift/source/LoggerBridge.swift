// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CaptureLoggerBridge
import Foundation

typealias LoggerID = Int64

/// Creates the directory we'll use for the SDK's ring buffer and other storage files,
/// and disables file protection on it to prevent the app from crashing with EXEC_BAD_ACCESS
/// when the device is locked. Failing to set the protection policy on the directory will result
/// in a nil logger.
///
/// - parameter path: The path to be created and/or set to none protection
func makeDirectoryAndDisableProtection(at path: String) throws {
    let url = NSURL(fileURLWithPath: path)
    let manager = FileManager.default
    if !manager.fileExists(atPath: path) {
        try manager.createDirectory(atPath: path, withIntermediateDirectories: true)
    }

    var fileProtection: AnyObject?
    try url.getResourceValue(&fileProtection, forKey: .fileProtectionKey)
    if let protection = fileProtection as? URLFileProtection, protection != .none {
        try url.setResourceValue(URLFileProtection.none, forKey: .fileProtectionKey)
    }
}

/// A wrapper around Rust logger ID that makes it possible to call Rust logger methods.
/// It shutdowns underlying Rust logger on release.
final class LoggerBridge: LoggerBridging {
    let loggerID: LoggerID
    private var blockingShutdown = false

    init?(
        apiKey: String,
        bufferDirectoryPath: String?,
        sessionStrategy: SessionStrategy,
        metadataProvider: CaptureLoggerBridge.MetadataProvider,
        resourceUtilizationTarget: CaptureLoggerBridge.ResourceUtilizationTarget,
        sessionReplayTarget: CaptureLoggerBridge.SessionReplayTarget,
        eventsListenerTarget: CaptureLoggerBridge.EventsListenerTarget,
        appID: String,
        releaseVersion: String,
        model: String,
        network: Network?,
        errorReporting: RemoteErrorReporting
    ) {
        do {
            try bufferDirectoryPath.map(makeDirectoryAndDisableProtection(at:))
        } catch {
            // To be safe we don't initialize the logger if we can't create the directory or set
            // the file protection policy.
            return nil
        }

        let loggerID = capture_create_logger(
            bufferDirectoryPath,
            apiKey,
            sessionStrategy.makeSessionStrategyProvider(),
            metadataProvider,
            resourceUtilizationTarget,
            sessionReplayTarget,
            eventsListenerTarget,
            appID,
            releaseVersion,
            model,
            network,
            errorReporting
        )

        if loggerID == -1 {
            return nil
        }

        self.loggerID = loggerID
    }

    deinit {
        // Blocking is needed to ensure that we do not exceed the limit of opened files while running
        // benchmarking tests.
        capture_shutdown_logger(self.loggerID, self.blockingShutdown)
    }

    // MARK: - LoggerBridging

    static func makeLogger(
        apiKey: String,
        bufferDirectoryPath: String?,
        sessionStrategy: SessionStrategy,
        metadataProvider: CaptureLoggerBridge.MetadataProvider,
        resourceUtilizationTarget: CaptureLoggerBridge.ResourceUtilizationTarget,
        sessionReplayTarget: CaptureLoggerBridge.SessionReplayTarget,
        eventsListenerTarget: CaptureLoggerBridge.EventsListenerTarget,
        appID: String,
        releaseVersion: String,
        model: String,
        network: Network?,
        errorReporting: RemoteErrorReporting
    ) -> LoggerBridging? {
        return LoggerBridge(
            apiKey: apiKey,
            bufferDirectoryPath: bufferDirectoryPath,
            sessionStrategy: sessionStrategy,
            metadataProvider: metadataProvider,
            resourceUtilizationTarget: resourceUtilizationTarget,
            sessionReplayTarget: sessionReplayTarget,
            eventsListenerTarget: eventsListenerTarget,
            appID: appID,
            releaseVersion: releaseVersion,
            model: model,
            network: network,
            errorReporting: errorReporting
        )
    }

    func start() {
        capture_start_logger(self.loggerID)
    }

    func log(
        level: LogLevel,
        message: @autoclosure () -> String,
        fields: [CapturePassable.Field]?,
        matchingFields: [CapturePassable.Field]?,
        type: Capture.Logger.LogType,
        blocking: Bool,
        occurredAtOverride: Date?
    ) {
        capture_write_log(
            self.loggerID,
            level.rawValue,
            type.rawValue,
            message(),
            fields,
            matchingFields,
            blocking,
            occurredAtOverride.map { Int64($0.timeIntervalSince1970 * 1_000) } ?? 0
        )
    }

    func logSessionReplayScreen(fields: [CapturePassable.Field], duration: TimeInterval) {
        capture_write_session_replay_screen_log(self.loggerID, fields, duration)
    }

    func logSessionReplayScreenshot(fields: [CapturePassable.Field], duration: TimeInterval) {
        capture_write_session_replay_screenshot_log(self.loggerID, fields, duration)
    }

    func logResourceUtilization(fields: [CapturePassable.Field], duration: TimeInterval) {
        capture_write_resource_utilization_log(self.loggerID, fields, duration)
    }

    func logSDKStart(fields: [CapturePassable.Field], duration: TimeInterval) {
        capture_write_sdk_start_log(self.loggerID, fields, duration)
    }

    func shouldLogAppUpdate(
        appVersion: String,
        buildNumber: String
    ) -> Bool {
        return capture_should_write_app_update_log(self.loggerID, appVersion, buildNumber)
    }

    func logAppUpdate(
        appVersion: String,
        buildNumber: String,
        appSizeBytes: UInt64,
        duration: TimeInterval
    ) {
        capture_write_app_update_log(self.loggerID, appVersion, buildNumber, appSizeBytes, duration)
    }

    func logAppLaunchTTI(_ duration: TimeInterval) {
        capture_write_app_launch_tti_log(self.loggerID, duration)
    }

    func logScreenView(screenName: String) {
        capture_write_screen_view_log(self.loggerID, screenName)
    }

    func startNewSession() {
        capture_start_new_session(self.loggerID)
    }

    func getSessionID() -> String {
        capture_get_session_id(self.loggerID)
    }

    func getDeviceID() -> String {
        capture_get_device_id(self.loggerID)
    }

    func addField(withKey key: String, value: String) {
        capture_add_log_field(self.loggerID, key, value)
    }

    func removeField(withKey key: String) {
        capture_remove_log_field(self.loggerID, key)
    }

    func flush(blocking: Bool) {
        capture_flush(self.loggerID, blocking)
    }

    func runtimeValue<T: RuntimeValue>(_ variable: RuntimeVariable<T>) -> T {
        return variable.load(loggerID: self.loggerID)
    }

    func handleError(context: String, error: Error) {
        capture_report_error(
            "swift layer: \(context): \(String(describing: error)); \(error.localizedDescription)"
        )
    }

    func enableBlockingShutdown() {
        self.blockingShutdown = true
    }
}
