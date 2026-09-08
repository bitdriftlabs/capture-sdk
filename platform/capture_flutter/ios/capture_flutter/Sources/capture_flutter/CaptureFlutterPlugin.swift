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
        case "logNetworkRequest":
            handleLogNetworkRequest(call, result: result)
        case "logNetworkResponse":
            handleLogNetworkResponse(call, result: result)
        case "startSpan":
            handleStartSpan(call, result: result)
        case "endSpan":
            handleEndSpan(call, result: result)
        case "logReplayScreen":
            handleLogReplayScreen(call, result: result)
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

        let configuration = Configuration(
            // Flutter provides its own wireframe data via logSessionReplayScreen.
            sessionReplayConfiguration: nil,
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

    private static func parseHTTPURLPath(_ any: Any?) -> HTTPURLPath? {
        guard let map = any as? [String: Any], let value = map["value"] as? String else {
            return nil
        }
        return HTTPURLPath(value: value, template: map["template"] as? String)
    }

    private static func parseHTTPRequestInfo(_ map: [String: Any]) -> HTTPRequestInfo? {
        guard let method = map["method"] as? String, let spanID = map["spanId"] as? String else {
            return nil
        }
        return HTTPRequestInfo(
            method: method,
            host: map["host"] as? String,
            path: Self.parseHTTPURLPath(map["path"]),
            query: map["query"] as? String,
            headers: map["headers"] as? [String: String],
            bytesExpectedToSendCount: (map["bytesExpectedToSendCount"] as? NSNumber)?.int64Value,
            spanID: spanID,
            extraFields: map["extraFields"] as? [String: String]
        )
    }

    private func handleLogNetworkRequest(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let request = Self.parseHTTPRequestInfo(args) else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing/invalid request", details: nil))
            return
        }
        Logger.log(request)
        result(nil)
    }

    private func handleLogNetworkResponse(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let requestMap = args["request"] as? [String: Any],
              let request = Self.parseHTTPRequestInfo(requestMap),
              let responseMap = args["response"] as? [String: Any],
              let resultStr = responseMap["result"] as? String,
              let durationMs = (args["durationMs"] as? NSNumber)?.doubleValue else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing/invalid response", details: nil))
            return
        }

        let httpResult: HTTPResponse.HTTPResult
        switch resultStr {
        case "success": httpResult = .success
        case "canceled": httpResult = .canceled
        default: httpResult = .failure
        }

        let error: Error? = (responseMap["error"] as? String).map {
            NSError(domain: "io.bitdrift.capture_flutter", code: 0, userInfo: [NSLocalizedDescriptionKey: $0])
        }

        let response = HTTPResponse(
            result: httpResult,
            host: responseMap["host"] as? String,
            path: Self.parseHTTPURLPath(responseMap["path"]),
            query: responseMap["query"] as? String,
            headers: responseMap["headers"] as? [String: String],
            statusCode: (responseMap["statusCode"] as? NSNumber)?.intValue,
            error: error
        )

        // Native metrics durations are seconds; the Dart API uses milliseconds throughout.
        let metricsMap = args["metrics"] as? [String: Any]
        let metrics: HTTPRequestMetrics? = metricsMap.map { m in
            func seconds(_ key: String) -> TimeInterval? {
                (m[key] as? NSNumber).map { $0.doubleValue / 1000.0 }
            }
            return HTTPRequestMetrics(
                requestBodyBytesSentCount: (m["requestBodyBytesSentCount"] as? NSNumber)?.int64Value,
                responseBodyBytesReceivedCount: (m["responseBodyBytesReceivedCount"] as? NSNumber)?.int64Value,
                requestHeadersBytesCount: (m["requestHeadersBytesCount"] as? NSNumber)?.int64Value,
                responseHeadersBytesCount: (m["responseHeadersBytesCount"] as? NSNumber)?.int64Value,
                dnsResolutionDuration: seconds("dnsResolutionDurationMs"),
                tlsDuration: seconds("tlsDurationMs"),
                tcpDuration: seconds("tcpDurationMs"),
                fetchInitializationDuration: seconds("fetchInitializationMs"),
                responseLatency: seconds("responseLatencyMs"),
                protocolName: m["protocolName"] as? String
            )
        }

        Logger.log(HTTPResponseInfo(
            requestInfo: request,
            response: response,
            duration: durationMs / 1000.0,
            metrics: metrics,
            extraFields: args["extraFields"] as? [String: String]
        ))
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

    private func handleLogReplayScreen(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard let args = call.arguments as? [String: Any],
              let screenData = args["screen"] as? FlutterStandardTypedData else {
            result(FlutterError(code: "INVALID_ARGS", message: "Missing screen data", details: nil))
            return
        }
        // logSessionReplayScreen is internal on the iOS SDK (requires @testable import).
        // No public API exists for this yet — placeholder logs replay size as info.
        // Track: expose logSessionReplayScreen on Logger public API.
        Logger.logInfo("_session_replay", fields: ["_replay_size": "\(screenData.data.count)"])
        result(nil)
    }
}
