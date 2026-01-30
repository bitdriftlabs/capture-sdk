// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import WebKit

/// Handles incoming messages from the WebView JavaScript bridge and routes them
/// to the appropriate logging methods.
final class WebViewBridgeMessageHandler: NSObject, WKScriptMessageHandler {
    private let logger: Logging
    
    private var currentPageSpanId: String?
    private var activePageViewSpans: [String: Span] = [:]
    
    init(logger: Logging) {
        self.logger = logger
        super.init()
    }
    
    // MARK: - WKScriptMessageHandler
    
    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage
    ) {
        guard let messageBody = message.body as? [String: Any] else {
            self.handleInternalError("WebView bridge message body is not a dictionary", error: nil)
            return
        }
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: messageBody),
              let bridgeMessage = try? JSONDecoder().decode(
                WebViewBridgeMessage.self,
                from: jsonData
              )
        else {
            self.handleInternalError("Failed to extract WebView bridge message", error: nil)
            return
        }
        
        if bridgeMessage.v != 1 {
            self.logger.log(
                level: .warning,
                message: "Unsupported WebView bridge protocol version",
                fields: ["_version": String(bridgeMessage.v)]
            )
            return
        }
        
        guard let type = bridgeMessage.type else {
            return
        }
        
        let timestamp = bridgeMessage.timestamp ?? Int64(Date().timeIntervalSince1970 * 1000)
        
        switch type {
        case "customLog":
            self.handleCustomLog(bridgeMessage, timestamp: timestamp)
        case "bridgeReady":
            self.handleBridgeReady(bridgeMessage)
        case "webVital":
            self.handleWebVital(bridgeMessage, timestamp: timestamp)
        case "networkRequest":
            self.handleNetworkRequest(bridgeMessage, timestamp: timestamp)
        case "navigation":
            self.handleNavigation(bridgeMessage, timestamp: timestamp)
        case "pageView":
            self.handlePageView(bridgeMessage, timestamp: timestamp)
        case "lifecycle":
            self.handleLifecycle(bridgeMessage, timestamp: timestamp)
        case "error":
            self.handleError(bridgeMessage, timestamp: timestamp)
        case "longTask":
            self.handleLongTask(bridgeMessage, timestamp: timestamp)
        case "resourceError":
            self.handleResourceError(bridgeMessage, timestamp: timestamp)
        case "console":
            self.handleConsole(bridgeMessage, timestamp: timestamp)
        case "promiseRejection":
            self.handlePromiseRejection(bridgeMessage, timestamp: timestamp)
        case "userInteraction":
            self.handleUserInteraction(bridgeMessage, timestamp: timestamp)
        case "internalAutoInstrumentation":
            self.handleInternalAutoInstrumentation(bridgeMessage, timestamp: timestamp)
        default:
            self.handleInternalError("Unknown WebView bridge message type: \(type)", error: nil)
        }
    }
    
    // MARK: - Message Handlers
    
    private func handleInternalAutoInstrumentation(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        guard let event = msg.event else { return }
        
        let fields: Fields = [
            "_event": event,
            "_source": "webview",
            "_timestamp": String(timestamp),
        ]
        
        self.logger.log(level: .debug, message: "[WebView] instrumented \(event)", fields: fields)
    }
    
    private func handleCustomLog(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        let levelStr = msg.level ?? "debug"
        let message = msg.message ?? ""
        
        var fields: Fields = [
            "_source": "webview",
            "_timestamp": String(timestamp),
        ]
        
        if let customFields = msg.fields {
            for (key, value) in customFields {
                fields[key] = String(describing: value.value)
            }
        }
        
        let level: LogLevel
        switch levelStr.lowercased() {
        case "info":
            level = .info
        case "warn":
            level = .warning
        case "error":
            level = .error
        case "trace":
            level = .trace
        default:
            level = .debug
        }
        
        self.logger.log(level: level, message: message, fields: fields)
    }
    
    private func handleBridgeReady(_ msg: WebViewBridgeMessage) {
        var fields: Fields = ["_source": "webview"]
        
        if let url = msg.url {
            fields["_url"] = url
        }
        
        if let config = msg.instrumentationConfig,
           let jsonData = try? JSONSerialization.data(withJSONObject: config.mapValues(\.value)),
           let jsonString = String(data: jsonData, encoding: .utf8)
        {
            fields["_config"] = jsonString
        }
        
        self.logger.log(level: .debug, message: "webview.initialized", fields: fields)
    }
    
    private func handleWebVital(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        guard let metric = msg.metric,
              let name = metric.name,
              let value = metric.value
        else {
            return
        }
        
        let rating = metric.rating ?? "unknown"
        let parentSpanId = msg.parentSpanId ?? self.currentPageSpanId
        
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
        
        var commonFields: Fields = [
            "_metric": name,
            "_value": String(value),
            "_rating": rating,
            "_source": "webview",
        ]
        
        if let delta = metric.delta {
            commonFields["_delta"] = String(delta)
        }
        if let id = metric.id {
            commonFields["_metric_id"] = id
        }
        if let navigationType = metric.navigationType {
            commonFields["_navigation_type"] = navigationType
        }
        if let parentSpanId {
            commonFields["_span_parent_id"] = parentSpanId
        }
        
        switch name {
        case "LCP":
            self.handleLCPMetric(metric, timestamp: timestamp, value: value, level: level, commonFields: commonFields, parentSpanId: parentSpanId)
        case "FCP":
            self.handleFCPMetric(metric, timestamp: timestamp, value: value, level: level, commonFields: commonFields, parentSpanId: parentSpanId)
        case "TTFB":
            self.handleTTFBMetric(metric, timestamp: timestamp, value: value, level: level, commonFields: commonFields, parentSpanId: parentSpanId)
        case "INP":
            self.handleINPMetric(metric, timestamp: timestamp, value: value, level: level, commonFields: commonFields, parentSpanId: parentSpanId)
        case "CLS":
            self.handleCLSMetric(metric, level: level, commonFields: commonFields)
        default:
            self.logger.log(level: level, message: "webview.webVital", fields: commonFields)
        }
    }
    
    private func handleLCPMetric(
        _ metric: WebVitalMetric,
        timestamp: Int64,
        value: Double,
        level: LogLevel,
        commonFields: Fields,
        parentSpanId: String?
    ) {
        var fields = commonFields
        fields["_metric"] = "LCP"
        
        if let entry = metric.entries?.first {
            if let element = entry.element {
                fields["_element"] = element
            }
            if let url = entry.url {
                fields["_url"] = url
            }
            if let size = entry.size {
                fields["_size"] = String(size)
            }
            if let renderTime = entry.renderTime {
                fields["_render_time"] = String(renderTime)
            }
            if let loadTime = entry.loadTime {
                fields["_load_time"] = String(loadTime)
            }
        }
        
        self.logWebVitalDurationSpan(timestamp: timestamp, durationMs: value, level: level, fields: fields, parentSpanId: parentSpanId)
    }
    
    private func handleFCPMetric(
        _ metric: WebVitalMetric,
        timestamp: Int64,
        value: Double,
        level: LogLevel,
        commonFields: Fields,
        parentSpanId: String?
    ) {
        var fields = commonFields
        fields["_metric"] = "FCP"
        
        if let entry = metric.entries?.first {
            if let name = entry.name {
                fields["_paint_type"] = name
            }
            if let startTime = entry.startTime {
                fields["_start_time"] = String(startTime)
            }
            if let entryType = entry.entryType {
                fields["_entry_type"] = entryType
            }
        }
        
        self.logWebVitalDurationSpan(timestamp: timestamp, durationMs: value, level: level, fields: fields, parentSpanId: parentSpanId)
    }
    
    private func handleTTFBMetric(
        _ metric: WebVitalMetric,
        timestamp: Int64,
        value: Double,
        level: LogLevel,
        commonFields: Fields,
        parentSpanId: String?
    ) {
        var fields = commonFields
        fields["_metric"] = "TTFB"
        
        if let entry = metric.entries?.first {
            if let domainLookupStart = entry.domainLookupStart {
                fields["_dns_start"] = String(domainLookupStart)
            }
            if let domainLookupEnd = entry.domainLookupEnd {
                fields["_dns_end"] = String(domainLookupEnd)
            }
            if let connectStart = entry.connectStart {
                fields["_connect_start"] = String(connectStart)
            }
            if let connectEnd = entry.connectEnd {
                fields["_connect_end"] = String(connectEnd)
            }
            if let secureConnectionStart = entry.secureConnectionStart {
                fields["_tls_start"] = String(secureConnectionStart)
            }
            if let requestStart = entry.requestStart {
                fields["_request_start"] = String(requestStart)
            }
            if let responseStart = entry.responseStart {
                fields["_response_start"] = String(responseStart)
            }
        }
        
        self.logWebVitalDurationSpan(timestamp: timestamp, durationMs: value, level: level, fields: fields, parentSpanId: parentSpanId)
    }
    
    private func handleINPMetric(
        _ metric: WebVitalMetric,
        timestamp: Int64,
        value: Double,
        level: LogLevel,
        commonFields: Fields,
        parentSpanId: String?
    ) {
        var fields = commonFields
        fields["_metric"] = "INP"
        
        if let entry = metric.entries?.first {
            if let name = entry.name {
                fields["_event_type"] = name
            }
            if let startTime = entry.startTime {
                fields["_interaction_time"] = String(startTime)
            }
            if let processingStart = entry.processingStart {
                fields["_processing_start"] = String(processingStart)
            }
            if let processingEnd = entry.processingEnd {
                fields["_processing_end"] = String(processingEnd)
            }
            if let duration = entry.duration {
                fields["_duration"] = String(duration)
            }
            if let interactionId = entry.interactionId {
                fields["_interaction_id"] = String(interactionId)
            }
        }
        
        self.logWebVitalDurationSpan(timestamp: timestamp, durationMs: value, level: level, fields: fields, parentSpanId: parentSpanId)
    }
    
    private func handleCLSMetric(
        _ metric: WebVitalMetric,
        level: LogLevel,
        commonFields: Fields
    ) {
        var fields = commonFields
        fields["_metric"] = "CLS"
        
        if let entries = metric.entries, !entries.isEmpty {
            var largestShiftValue = 0.0
            var largestShiftTime = 0.0
            
            for entry in entries {
                if let shiftValue = entry.value, shiftValue > largestShiftValue {
                    largestShiftValue = shiftValue
                    if let startTime = entry.startTime {
                        largestShiftTime = startTime
                    }
                }
            }
            
            if largestShiftValue > 0 {
                fields["_largest_shift_value"] = String(largestShiftValue)
                fields["_largest_shift_time"] = String(largestShiftTime)
            }
            
            fields["_shift_count"] = String(entries.count)
        }
        
        self.logger.log(level: level, message: "webview.webVital", fields: fields)
    }
    
    private func logWebVitalDurationSpan(
        timestamp: Int64,
        durationMs: Double,
        level: LogLevel,
        fields: Fields,
        parentSpanId: String?
    ) {
        let startTimeMs = timestamp - Int64(durationMs)
        let startTimeInterval = TimeInterval(startTimeMs) / 1000.0
        let endTimeInterval = TimeInterval(timestamp) / 1000.0
        
        let result: SpanResult
        switch fields["_rating"] as? String {
        case "good":
            result = .success
        case "needs-improvement", "poor":
            result = .failure
        default:
            result = .unknown
        }
        
        let parentUuid = parentSpanId.flatMap { UUID(uuidString: $0) }
        
        let span = self.logger.startSpan(
            name: "webview.webVital",
            level: level,
            file: nil,
            line: nil,
            function: nil,
            fields: fields,
            startTimeInterval: startTimeInterval,
            parentSpanID: parentUuid
        )
        
        span.end(result, fields: fields, endTimeInterval: endTimeInterval)
    }
    
    private func handleNetworkRequest(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        let method = msg.method ?? "GET"
        guard let url = msg.url else { return }
        
        let statusCode = msg.statusCode
        let durationMs = msg.durationMs ?? 0
        let success = msg.success ?? false
        let errorMessage = msg.error
        let requestType = msg.requestType ?? "unknown"
        
        let uri = URL(string: url)
        let host = uri?.host
        let path = uri?.path
        let query = uri?.query
        
        let extraFields: Fields = [
            "_source": "webview",
            "_request_type": requestType,
            "_timestamp": String(timestamp),
        ]
        
        let requestInfo = HTTPRequestInfo(
            method: method,
            host: host,
            path: path.map { HTTPURLPath(value: $0) },
            query: query,
            extraFields: extraFields
        )
        
        var metrics: HTTPRequestMetrics?
        if let timing = msg.timing {
            metrics = HTTPRequestMetrics(
                requestBodyBytesSentCount: 0,
                responseBodyBytesReceivedCount: timing.transferSize ?? 0,
                requestHeadersBytesCount: 0,
                responseHeadersBytesCount: 0,
                dnsResolutionDuration: timing.dnsMs.map { TimeInterval($0) / 1000.0 },
                tlsDuration: timing.tlsMs.map { TimeInterval($0) / 1000.0 },
                tcpDuration: timing.connectMs.map { TimeInterval($0) / 1000.0 },
                responseLatency: timing.ttfbMs.map { TimeInterval($0) / 1000.0 }
            )
        }
        
        let result: HTTPResponse.HTTPResult = success ? .success : .failure
        
        let error = errorMessage.map { NSError(domain: "WebView", code: 0, userInfo: [NSLocalizedDescriptionKey: $0]) as Error }
        
        let response = HTTPResponse(
            result: result,
            statusCode: statusCode,
            error: error
        )
        
        let duration = TimeInterval(durationMs) / 1000.0
        let responseInfo = HTTPResponseInfo(
            requestInfo: requestInfo,
            response: response,
            duration: duration,
            metrics: metrics
        )
        
        self.logger.log(requestInfo)
        self.logger.log(responseInfo)
    }
    
    private func handlePageView(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        guard let action = msg.action,
              let spanId = msg.spanId
        else {
            return
        }
        
        let url = msg.url ?? ""
        let reason = msg.reason ?? ""
        
        switch action {
        case "start":
            self.currentPageSpanId = spanId
            
            let fields: Fields = [
                "_span_id": spanId,
                "_url": url,
                "_reason": reason,
                "_source": "webview",
                "_timestamp": String(timestamp),
            ]
            
            let startTime = TimeInterval(timestamp) / 1000.0
            let span = self.logger.startSpan(
                name: "webview.pageView",
                level: .debug,
                file: nil,
                line: nil,
                function: nil,
                fields: fields,
                startTimeInterval: startTime,
                parentSpanID: nil
            )
            self.activePageViewSpans[spanId] = span
            
        case "end":
            var fields: Fields = [
                "_span_id": spanId,
                "_url": url,
                "_reason": reason,
                "_source": "webview",
                "_timestamp": String(timestamp),
            ]
            
            if let durationMs = msg.durationMs {
                fields["_duration_ms"] = String(durationMs)
            }
            
            let endTime = TimeInterval(timestamp) / 1000.0
            self.activePageViewSpans.removeValue(forKey: spanId)?.end(
                .success,
                fields: fields,
                endTimeInterval: endTime
            )
            
            if self.currentPageSpanId == spanId {
                self.currentPageSpanId = nil
            }
            
        default:
            break
        }
    }
    
    private func handleLifecycle(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        guard let event = msg.event else { return }
        
        var fields: Fields = [
            "_event": event,
            "_source": "webview",
            "_timestamp": String(timestamp),
        ]
        
        if let performanceTime = msg.performanceTime {
            fields["_performance_time"] = String(performanceTime)
        }
        if let visibilityState = msg.visibilityState {
            fields["_visibility_state"] = visibilityState
        }
        
        self.logger.log(level: .debug, message: "webview.lifecycle", fields: fields)
    }
    
    private func handleNavigation(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        let fromUrl = msg.fromUrl ?? ""
        let toUrl = msg.toUrl ?? ""
        let method = msg.method ?? ""
        
        let fields: Fields = [
            "_from_url": fromUrl,
            "_to_url": toUrl,
            "_method": method,
            "_source": "webview",
            "_timestamp": String(timestamp),
        ]
        
        self.logger.log(level: .debug, message: "webview.navigation", fields: fields)
    }
    
    private func handleError(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        let name = msg.name ?? "Error"
        let errorMessage = msg.message ?? "Unknown error"
        
        var fields: Fields = [
            "_name": name,
            "_message": errorMessage,
            "_source": "webview",
            "_timestamp": String(timestamp),
        ]
        
        if let stack = msg.stack {
            fields["_stack"] = stack
        }
        if let filename = msg.filename {
            fields["_filename"] = filename
        }
        if let lineno = msg.lineno {
            fields["_lineno"] = String(lineno)
        }
        if let colno = msg.colno {
            fields["_colno"] = String(colno)
        }
        
        self.logger.log(level: .error, message: "webview.error", fields: fields)
    }
    
    private func handleLongTask(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        guard let durationMs = msg.durationMs else { return }
        
        var fields: Fields = [
            "_duration_ms": String(durationMs),
            "_source": "webview",
            "_timestamp": String(timestamp),
        ]
        
        if let startTime = msg.startTime {
            fields["_start_time"] = String(startTime)
        }
        
        if let attribution = msg.attribution {
            if let name = attribution.name {
                fields["_attribution_name"] = name
            }
            if let containerType = attribution.containerType {
                fields["_container_type"] = containerType
            }
            if let containerSrc = attribution.containerSrc {
                fields["_container_src"] = containerSrc
            }
            if let containerId = attribution.containerId {
                fields["_container_id"] = containerId
            }
            if let containerName = attribution.containerName {
                fields["_container_name"] = containerName
            }
        }
        
        let level: LogLevel
        if durationMs >= 200 {
            level = .warning
        } else if durationMs >= 100 {
            level = .info
        } else {
            level = .debug
        }
        
        self.logger.log(level: level, message: "webview.longTask", fields: fields)
    }
    
    private func handleResourceError(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        let fields: Fields = [
            "_resource_type": msg.resourceType ?? "unknown",
            "_url": msg.url ?? "",
            "_tag_name": msg.tagName ?? "",
            "_source": "webview",
            "_timestamp": String(timestamp),
        ]
        
        self.logger.log(level: .warning, message: "webview.resourceError", fields: fields)
    }
    
    private func handleConsole(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        let level = msg.level ?? "log"
        let consoleMessage = msg.message ?? ""
        
        var fields: Fields = [
            "_level": level,
            "_message": consoleMessage,
            "_source": "webview",
            "_timestamp": String(timestamp),
        ]
        
        if let args = msg.args, !args.isEmpty {
            let argsString = args.prefix(5).joined(separator: ", ")
            fields["_args"] = argsString
        }
        
        let logLevel: LogLevel
        switch level {
        case "error":
            logLevel = .error
        case "warn":
            logLevel = .warning
        case "info":
            logLevel = .info
        default:
            logLevel = .debug
        }
        
        self.logger.log(level: logLevel, message: "webview.console", fields: fields)
    }
    
    private func handlePromiseRejection(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        let reason = msg.reason ?? "Unknown rejection"
        
        var fields: Fields = [
            "_reason": reason,
            "_source": "webview",
            "_timestamp": String(timestamp),
        ]
        
        if let stack = msg.stack {
            fields["_stack"] = stack
        }
        
        self.logger.log(level: .error, message: "webview.promiseRejection", fields: fields)
    }
    
    private func handleUserInteraction(_ msg: WebViewBridgeMessage, timestamp: Int64) {
        guard let interactionType = msg.interactionType else { return }
        
        var fields: Fields = [
            "_interaction_type": interactionType,
            "_tag_name": msg.tagName ?? "",
            "_is_clickable": String(msg.isClickable ?? false),
            "_source": "webview",
            "_timestamp": String(timestamp),
        ]
        
        if let elementId = msg.elementId {
            fields["_element_id"] = elementId
        }
        if let className = msg.className {
            fields["_class_name"] = className
        }
        if let textContent = msg.textContent {
            fields["_text_content"] = textContent
        }
        if let clickCount = msg.clickCount {
            fields["_click_count"] = String(clickCount)
        }
        if let timeWindowMs = msg.timeWindowMs {
            fields["_time_window_ms"] = String(timeWindowMs)
        }
        if let duration = msg.duration {
            fields["_duration"] = String(duration)
        }
        
        let level: LogLevel = interactionType == "rageClick" ? .warning : .debug
        self.logger.log(level: level, message: "webview.userInteraction", fields: fields)
    }
    
    private func handleInternalError(_ message: String, error: Error?) {
        var fields: Fields = ["_source": "webview"]
        
        if let error {
            fields["_error"] = error.localizedDescription
        }
        
        self.logger.log(level: .error, message: message, fields: fields)
    }
}
