// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture

@objc public class CAPRNLogger: NSObject {
    @objc public static func start(key: String, sessionStrategy: String, url: String?, enableNetworkInstrumentation: Bool) {
        let strategy = switch sessionStrategy {
        case "fixed":
            SessionStrategy.fixed()
        case "activity":
            SessionStrategy.activityBased()
        default:
            SessionStrategy.fixed()
        }
        
        let integrator = Capture.Logger.start(
            withAPIKey: key,
            sessionStrategy: strategy,
            apiURL: URL(string: url ?? "https://api.bitdrift.io")!
        )
        
        if enableNetworkInstrumentation {
            integrator?.enableIntegrations([.urlSession()])
        }
    }
    
    @objc
    public static func getDeviceID(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let deviceID = Capture.Logger.deviceID
        if (deviceID == nil) {
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
    public static func getSessionID(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let sessionID = Capture.Logger.sessionID
        if (sessionID == nil) {
            reject("session_id_undefined", "Session ID is undefined", nil)
            return
        }
        resolve(sessionID)
    }
    
    @objc
    public static func getSessionURL(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let sessionURL = Capture.Logger.sessionURL
        if (sessionURL == nil) {
            reject("session_url_undefined", "Session URL is undefined", nil)
            return
        }
        resolve(sessionURL)
    }
}
