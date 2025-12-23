// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
@preconcurrency import WebKit

/// Handles incoming messages from the WebView JavaScript bridge
final class WebViewMessageHandler: NSObject, WKScriptMessageHandler {
    private weak var logger: Logging?
    
    /// Whether the bridge has signaled it's ready
    var bridgeReady = false
    
    init(logger: Logging?) {
        self.logger = logger
        super.init()
    }
    
    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage
    ) {
        guard let body = message.body as? [String: Any] else {
            logger?.log(
                level: .warning,
                message: "Invalid message format from WebView bridge",
                fields: nil
            )
            return
        }
        
        handleMessage(body)
    }
    
    private func handleMessage(_ message: [String: Any]) {
        // Check protocol version
        guard let version = message["v"] as? Int, version == 1 else {
            let v = message["v"] as? Int ?? 0
            logger?.log(
                level: .warning,
                message: "Unsupported WebView bridge protocol version",
                fields: ["_version": String(v)]
            )
            return
        }
        
        guard let type = message["type"] as? String else { return }
        
        switch type {
        case "bridgeReady":
            handleBridgeReady(message)
        case "webVital":
            handleWebVital(message)
        case "networkRequest":
            handleNetworkRequest(message)
        case "navigation":
            handleNavigation(message)
        case "error":
            handleError(message)
        default:
            break
        }
    }
    
    private func handleBridgeReady(_ message: [String: Any]) {
        bridgeReady = true
        let url = message["url"] as? String ?? ""
        logger?.log(
            level: .debug,
            message: "WebView bridge ready",
            fields: ["_url": url]
        )
    }
    
    private func handleWebVital(_ message: [String: Any]) {
        guard let name = message["name"] as? String,
              let value = message["value"] as? Double else { return }
        
        let rating = message["rating"] as? String ?? "unknown"
        let navigationType = message["navigationType"] as? String
        
        var fields: Fields = [
            "_metric": name,
            "_value": String(value),
            "_rating": rating,
            "_source": "webview"
        ]
        
        if let navType = navigationType {
            fields["_navigationType"] = navType
        }
        
        let level: LogLevel
        switch rating {
        case "good":
            level = .debug
        case "needs-improvement":
            level = .info
        case "poor":
            level = .warning
        default:
            level = .debug
        }
        
        logger?.log(
            level: level,
            message: "webview.webVital.\(name)",
            fields: fields
        )
    }
    
    private func handleNetworkRequest(_ message: [String: Any]) {
        guard let requestId = message["requestId"] as? String,
              let url = message["url"] as? String else { return }
        
        let method = message["method"] as? String ?? "GET"
        let statusCode = message["statusCode"] as? Int ?? 0
        let durationMs = message["durationMs"] as? Int ?? 0
        let success = message["success"] as? Bool ?? false
        let error = message["error"] as? String
        let requestType = message["requestType"] as? String ?? "unknown"
        
        var fields: Fields = [
            "_url": url,
            "_method": method,
            "_statusCode": String(statusCode),
            "_durationMs": String(durationMs),
            "_requestType": requestType,
            "_source": "webview"
        ]
        
        if let err = error {
            fields["_error"] = err
        }
        
        // Extract timing data if available
        if let timing = message["timing"] as? [String: Any] {
            if let dnsMs = timing["dnsMs"] as? Double {
                fields["_dnsMs"] = String(dnsMs)
            }
            if let connectMs = timing["connectMs"] as? Double {
                fields["_connectMs"] = String(connectMs)
            }
            if let tlsMs = timing["tlsMs"] as? Double {
                fields["_tlsMs"] = String(tlsMs)
            }
            if let ttfbMs = timing["ttfbMs"] as? Double {
                fields["_ttfbMs"] = String(ttfbMs)
            }
            if let downloadMs = timing["downloadMs"] as? Double {
                fields["_downloadMs"] = String(downloadMs)
            }
            if let transferSize = timing["transferSize"] as? Int {
                fields["_transferSize"] = String(transferSize)
            }
        }
        
        let level: LogLevel = success ? .debug : .warning
        
        logger?.log(
            level: level,
            message: "webview.network",
            fields: fields
        )
    }
    
    private func handleNavigation(_ message: [String: Any]) {
        let fromUrl = message["fromUrl"] as? String ?? ""
        let toUrl = message["toUrl"] as? String ?? ""
        let method = message["method"] as? String ?? ""
        
        let fields: Fields = [
            "_fromUrl": fromUrl,
            "_toUrl": toUrl,
            "_method": method,
            "_source": "webview"
        ]
        
        logger?.log(
            level: .debug,
            message: "webview.navigation",
            fields: fields
        )
    }
    
    private func handleError(_ message: [String: Any]) {
        let errorMessage = message["message"] as? String ?? "Unknown error"
        let stack = message["stack"] as? String
        let filename = message["filename"] as? String
        let lineno = message["lineno"] as? Int
        let colno = message["colno"] as? Int
        
        var fields: Fields = [
            "_message": errorMessage,
            "_source": "webview"
        ]
        
        if let s = stack {
            fields["_stack"] = String(s.prefix(1000))
        }
        if let f = filename {
            fields["_filename"] = f
        }
        if let l = lineno {
            fields["_lineno"] = String(l)
        }
        if let c = colno {
            fields["_colno"] = String(c)
        }
        
        logger?.log(
            level: .error,
            message: "webview.error",
            fields: fields
        )
    }
}
