// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import Foundation

// Must match src/index.tsx ISSUE_REPORT_EVENT and Android/iOS bridge constants.
let CAPRNIssueReportDidEmitNotification = Notification.Name("BdReactNative.onBeforeReportSend")
let CAPRNStartResultDidEmitNotification = Notification.Name("BdReactNative.onStartResult")

@objc public class CAPRNLogger: NSObject {
    
    private static let debugId: String? = {
        DebugId.fromBundle()
    }()
    
    @objc public static func start(
        key: String,
        sessionStrategy: String,
        url: String?,
        enableNetworkInstrumentation: Bool,
        enableNativeFatalIssues: Bool,
        enableJsErrors: Bool,
        enableIssueCallbackBridge: Bool,
        enableStartResultBridge: Bool
    ) {
        ReportDirectory.setupWatcherDirectory(enableJsErrors: enableJsErrors)
        
        let strategy =
            switch sessionStrategy {
            case "fixed":
                SessionStrategy.fixed()
            case "activity":
                SessionStrategy.activityBased()
            default:
                SessionStrategy.fixed()
            }

        let issueCallbackConfiguration: IssueCallbackConfiguration?
        if enableIssueCallbackBridge {
            issueCallbackConfiguration = IssueCallbackConfiguration(
                callbackQueue: DispatchQueue(label: "io.bitdrift.capture.reactnative.issue-callback", qos: .utility),
                issueReportCallback: CAPRNIssueReportCallback()
            )
        } else {
            issueCallbackConfiguration = nil
        }

        let configuration = Configuration(
            enableFatalIssueReporting: enableNativeFatalIssues,
            apiURL: URL(string: url ?? "https://api.bitdrift.io")!,
            issueCallbackConfiguration: issueCallbackConfiguration
        )

        let integrator = Capture.Logger.start(
            withAPIKey: key,
            sessionStrategy: strategy,
            configuration: configuration,
            startResult: { result in
                guard enableStartResultBridge else {
                    return
                }

                switch result {
                case .success:
                    NotificationCenter.default.post(
                        name: CAPRNStartResultDidEmitNotification,
                        object: nil,
                        userInfo: ["success": true]
                    )
                case .failure(let error):
                    NotificationCenter.default.post(
                        name: CAPRNStartResultDidEmitNotification,
                        object: nil,
                        userInfo: [
                            "success": false,
                            "error": error.localizedDescription,
                        ]
                    )
                }
            }
        )
        

        if enableNetworkInstrumentation {
            integrator?.enableIntegrations([.urlSession()])
        }
    }

    @objc
    public static func getDeviceID(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        let deviceID = Capture.Logger.deviceID
        if deviceID == nil {
            reject("device_id_undefined", "Device ID is undefined", nil)
            return
        }
        resolve(deviceID)
    }

    @objc
    public static func log(_ level: Double, message: String, fields: [String: String]) {
        switch level {
        case 0.0:
            Capture.Logger.logTrace(message, fields: fields)
        case 1.0:
            Capture.Logger.logDebug(message, fields: fields)
        case 2.0:
            Capture.Logger.logInfo(message, fields: fields)
        case 3.0:
            Capture.Logger.logWarning(message, fields: fields)
        case 4.0:
            Capture.Logger.logError(message, fields: fields)
        default:
            return
        }
    }

    @objc
    public static func addField(_ key: String, value: String) {
        Capture.Logger.addField(withKey: key, value: value)
    }

    @objc
    public static func removeField(_ key: String) {
        Capture.Logger.removeField(withKey: key)
    }

    @objc
    public static func isTracingActive() -> Bool {
        Capture.Logger.isTracingActive
    }

    @objc
    public static func setEntityId(_ entityId: String) {
        Capture.Logger.setEntityID(entityId)
    }

    @objc
    public static func clearEntityId() {
        Capture.Logger.clearEntityID()
    }

    @objc
    public static func getSessionID(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        let sessionID = Capture.Logger.sessionID
        if sessionID == nil {
            reject("session_id_undefined", "Session ID is undefined", nil)
            return
        }
        resolve(sessionID)
    }

    @objc
    public static func getSessionURL(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
    ) {
        let sessionURL = Capture.Logger.sessionURL
        if sessionURL == nil {
            reject("session_url_undefined", "Session URL is undefined", nil)
            return
        }
        resolve(sessionURL)
    }

    @objc
    public static func getPreviousRunInfo() -> [String: Any]? {
        guard let info = Capture.Logger.previousRunInfo else {
            return nil
        }
        return [
            "hasFatallyTerminated": info.hasFatallyTerminated,
            "terminationReason": info.terminationReason.rawValue,
        ]
    }

    @objc
    public static func getSdkStatus() -> [String: Any] {
        let status = Capture.Logger.getSdkStatus()
        var payload: [String: Any] = [
            "initializationState": status.initializationState.jsValue,
        ]

        if let lastHandshakeTime = status.lastHandshakeTime {
            payload["lastHandshakeTimeMs"] = Int64(lastHandshakeTime.timeIntervalSince1970 * 1000)
        }

        if let lastConfigDeliveryTime = status.lastConfigDeliveryTime {
            payload["lastConfigDeliveryTimeMs"] = Int64(lastConfigDeliveryTime.timeIntervalSince1970 * 1000)
        }

        return payload
    }

    @objc
    public static func logScreenView(screenName: String) {
        Capture.Logger.logScreenView(screenName: screenName)
    }

    @objc
    public static func logAppLaunchTTI(TTI: TimeInterval) {
        Capture.Logger.logAppLaunchTTI(TTI)
    }

    @objc
    public static func reportJsError(
        errorName: String,
        message: String,
        stack: String,
        isFatal: Bool,
        engine: String,
        reactNativeVersion: String
    ) {
        guard let destinationPath = ReportDirectory.destinationPath(isFatal: isFatal) else {
            return
        }
        
        let debugId = Self.debugId ?? ""
        let deviceMetadata = DeviceMetadata.current()
        let appMetadata = AppMetadata.current()
        
        let timeInterval = Date().timeIntervalSince1970
        let timestampSeconds = UInt64(timeInterval)
        let fractionalSeconds = timeInterval - Double(timestampSeconds)
        let timestampNanos = UInt32(fractionalSeconds * 1_000_000_000)
        
        JavaScriptErrorReport.persist(
            errorName: errorName,
            message: message,
            stack: stack,
            isFatal: isFatal,
            engine: engine,
            debugId: debugId,
            timestampSeconds: timestampSeconds,
            timestampNanos: timestampNanos,
            destinationPath: destinationPath,
            deviceMetadata: deviceMetadata,
            appMetadata: appMetadata,
            sdkVersion: reactNativeVersion
        )
    }

    @objc
    public static func setFeatureFlagExposureString(name: String, variant: String) {
        Capture.Logger.setFeatureFlagExposure(withName: name, variant: variant)
    }

    @objc
    public static func setFeatureFlagExposureBool(name: String, variant: Bool) {
        Capture.Logger.setFeatureFlagExposure(withName: name, variant: variant)
    }
}

private final class CAPRNIssueReportCallback: NSObject, IssueReportCallback {
    func onBeforeReportSend(report: IssueReport) {
        NotificationCenter.default.post(
            name: CAPRNIssueReportDidEmitNotification,
            object: nil,
            userInfo: [
                "reportType": report.reportType,
                "reason": report.reason,
                "details": report.details,
                "sessionId": report.sessionID,
                "fields": report.fields,
            ]
        )
    }
}

private extension InitializationState {
    var jsValue: String {
        switch self {
        case .notStarted:
            return "notStarted"
        case .loaded:
            return "loaded"
        case .running:
            return "running"
        case .disabled:
            return "disabled"
        }
    }
}
