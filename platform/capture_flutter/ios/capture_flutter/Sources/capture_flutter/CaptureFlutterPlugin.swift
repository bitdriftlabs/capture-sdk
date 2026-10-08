// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import Flutter
import UIKit

public class CaptureFlutterPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: "io.bitdrift.capture_flutter",
            binaryMessenger: registrar.messenger()
        )
        let instance = CaptureFlutterPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    private var activeSpans: [String: Any] = [:]

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "start":
            handleStart(call, result: result)
        case "log":
            handleLog(call, result: result)
        case "logScreenView":
            handleLogScreenView(call, result: result)
        case "getSessionId":
            result(Logger.sessionID)
        case "getSessionUrl":
            result(Logger.sessionURL)
        case "getDeviceId":
            result(Logger.deviceID)
        case "createTemporaryDeviceCode":
            handleCreateTemporaryDeviceCode(result: result)
        case "startNewSession":
            Logger.startNewSession()
            result(nil)
        case "getSdkStatus":
            let status = Logger.getSdkStatus()
            let stateName: String
            switch status.initializationState {
            case .notStarted: stateName = "NOT_STARTED"
            case .loaded: stateName = "LOADED"
            case .running: stateName = "RUNNING"
            case .disabled: stateName = "DISABLED"
            @unknown default: stateName = "UNKNOWN"
            }
            result([
                "initializationState": stateName,
                "lastHandshakeTime": status.lastHandshakeTime?.timeIntervalSince1970,
                "lastConfigDeliveryTime": status.lastConfigDeliveryTime?.timeIntervalSince1970,
            ] as [String: Any?])

        case "addField":
            guard let args = call.arguments as? [String: Any],
                  let key = args["key"] as? String,
                  let value = args["value"] as? String else {
                result(FlutterError(code: "INVALID_ARGS", message: "Missing key/value", details: nil))
                return
            }
            Logger.addField(withKey: key, value: value)
            result(nil)
        case "removeField":
            guard let args = call.arguments as? [String: Any],
                  let key = args["key"] as? String else {
                result(FlutterError(code: "INVALID_ARGS", message: "Missing key", details: nil))
                return
            }
            Logger.removeField(withKey: key)
            result(nil)
        case "setEntityId":
            guard let args = call.arguments as? [String: Any],
                  let entityId = args["entityId"] as? String else {
                result(FlutterError(code: "INVALID_ARGS", message: "Missing entityId", details: nil))
                return
            }
            Logger.setEntityID(entityId)
            result(nil)
        case "clearEntityId":
            Logger.clearEntityID()
            result(nil)
        case "startSpan":
            handleStartSpan(call, result: result)
        case "endSpan":
            handleEndSpan(call, result: result)
        case "updateReplayRects":
            handleUpdateReplayRects(call, result: result)
        case "getReportContext":
            result(Self.reportContext())
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func handleStart(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let apiKey = args["apiKey"] as? String else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing apiKey", details: nil))
            return
        }
        let apiUrl = args["apiUrl"] as? String ?? "https://api.bitdrift.io"
        let strategyName = args["sessionStrategy"] as? String ?? "fixed"
        let sessionStrategy: SessionStrategy = strategyName == "activityBased"
            ? .activityBased()
            : .fixed()

        let enableSessionReplay = args["enableSessionReplay"] as? Bool ?? false
        let sessionReplayConfiguration = enableSessionReplay
            ? SessionReplayConfiguration(categorizers: ["FlutterView": AnnotatedView(.ignore)])
            : nil
        let configuration = Configuration(
            sessionReplayConfiguration: sessionReplayConfiguration,
            apiURL: URL(string: apiUrl)!
        )

        Logger.start(
            withAPIKey: apiKey,
            sessionStrategy: sessionStrategy,
            configuration: configuration
        )
        result(true)
    }

    private func handleLog(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let message = args["message"] as? String,
              let levelStr = args["level"] as? String else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing params", details: nil))
            return
        }
        let fields = (args["fields"] as? [String: String]) ?? [:]
        switch levelStr {
        case "trace": Logger.logTrace(message, fields: fields)
        case "debug": Logger.logDebug(message, fields: fields)
        case "info": Logger.logInfo(message, fields: fields)
        case "warning": Logger.logWarning(message, fields: fields)
        case "error": Logger.logError(message, fields: fields)
        default: Logger.logInfo(message, fields: fields)
        }
        result(nil)
    }

    private func handleCreateTemporaryDeviceCode(result: @escaping FlutterResult) {
        Logger.createTemporaryDeviceCode { deviceCodeResult in
            switch deviceCodeResult {
            case .success(let deviceCode):
                result(deviceCode)
            case .failure(let error):
                result(FlutterError(code: "DEVICE_CODE_ERROR", message: error.localizedDescription, details: nil))
            }
        }
    }

    private func handleLogScreenView(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let screenName = args["screenName"] as? String else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing screenName", details: nil))
            return
        }
        Logger.logScreenView(screenName: screenName)
        result(nil)
    }

    private func handleStartSpan(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let name = args["name"] as? String else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing name", details: nil))
            return
        }
        let levelStr = args["level"] as? String ?? "info"
        let level: Capture.LogLevel
        switch levelStr {
        case "trace": level = .trace
        case "debug": level = .debug
        case "warning": level = .warning
        case "error": level = .error
        default: level = .info
        }
        let fields = (args["fields"] as? [String: String]) ?? [:]
        if let span = Logger.startSpan(name: name, level: level, fields: fields) {
            let spanId = "\(ObjectIdentifier(span as AnyObject).hashValue)"
            activeSpans[spanId] = span
            result(spanId)
        } else {
            result(nil)
        }
    }

    private func handleEndSpan(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let spanId = args["spanId"] as? String else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing spanId", details: nil))
            return
        }
        if let span = activeSpans.removeValue(forKey: spanId) as? Span {
            let success = args["success"] as? Bool ?? true
            if success {
                span.end(.success)
            } else {
                span.end(.failure)
            }
        }
        result(nil)
    }

    private func handleUpdateReplayRects(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let rectsData = args["rects"] as? FlutterStandardTypedData else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing rects", details: nil))
            return
        }

        let values: [Int32] = rectsData.data.withUnsafeBytes { Array($0.bindMemory(to: Int32.self)) }
        var rects: [(frame: CGRect, type: ViewType)] = []
        rects.reserveCapacity(values.count / 5)
        var index = 0
        while index + 4 < values.count {
            if let type = ViewType(rawValue: UInt8(truncatingIfNeeded: values[index])) {
                let frame = CGRect(
                    x: CGFloat(values[index + 1]),
                    y: CGFloat(values[index + 2]),
                    width: CGFloat(values[index + 3]),
                    height: CGFloat(values[index + 4])
                )
                rects.append((frame, type))
            }
            index += 5
        }

        let windows = UIApplication.shared.connectedScenes.flatMap { ($0 as? UIWindowScene)?.windows ?? [] }
        FlutterReplayOverlayView.attached(in: windows)?.update(rects: rects)
        result(nil)
    }

    private static func reportContext() -> [String: Any?] {
        let sdkDirectory = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)
            .first!
            .appendingPathComponent("bitdrift_capture")
        let info = Bundle.main.infoDictionary ?? [:]

        var systemInfo = utsname()
        uname(&systemInfo)
        let model = withUnsafeBytes(of: &systemInfo.machine) { buffer in
            String(decoding: buffer.prefix { $0 != 0 }, as: UTF8.self)
        }

        return [
            "sdkDirectory": sdkDirectory.path,
            "appId": Bundle.main.bundleIdentifier,
            "appVersion": info["CFBundleShortVersionString"] as? String,
            "buildNumber": info["CFBundleVersion"] as? String,
            "osVersion": UIDevice.current.systemVersion,
            "osBuildVersion": Self.sysctlString("kern.osversion"),
            "manufacturer": "Apple",
            "model": ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] ?? model,
        ]
    }

    private static func sysctlString(_ name: String) -> String? {
        var size = 0
        guard sysctlbyname(name, nil, &size, nil, 0) == 0, size > 0 else {
            return nil
        }

        var buffer = [CChar](repeating: 0, count: size)
        guard sysctlbyname(name, &buffer, &size, nil, 0) == 0 else {
            return nil
        }

        return String(cString: buffer)
    }
}
