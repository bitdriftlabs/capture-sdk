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
    
    /// Current page view span ID for nesting child events
    private var currentPageSpanId: String?
    
    /// Active page view spans, keyed by span ID
    private var activePageViewSpans: [String: Span] = [:]
    
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
        case "pageView":
            handlePageView(message)
        case "lifecycle":
            handleLifecycle(message)
        case "error":
            handleError(message)
        case "longTask":
            handleLongTask(message)
        case "resourceError":
            handleResourceError(message)
        case "console":
            handleConsole(message)
        case "promiseRejection":
            handlePromiseRejection(message)
        case "userInteraction":
            handleUserInteraction(message)
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
        guard let metric = message["metric"] as? [String: Any],
              let name = metric["name"] as? String,
              let value = metric["value"] as? Double else { return }
        
        let rating = metric["rating"] as? String ?? "unknown"
        let delta = metric["delta"] as? Double
        let id = metric["id"] as? String
        let navigationType = metric["navigationType"] as? String
        let entries = metric["entries"] as? [[String: Any]]
        let timestamp = message["timestamp"] as? Double ?? (Date().timeIntervalSince1970 * 1000)
        
        // Extract parentSpanId from the message (set by JS SDK)
        let parentSpanId = message["parentSpanId"] as? String ?? currentPageSpanId
        
        // Determine log level based on rating
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
        
        // Build common fields for all web vitals
        var commonFields: Fields = [
            "_metric": name,
            "_value": String(value),
            "_rating": rating,
            "_source": "webview"
        ]
        
        if let d = delta {
            commonFields["_delta"] = String(d)
        }
        if let i = id {
            commonFields["_metric_id"] = i
        }
        if let navType = navigationType {
            commonFields["_navigation_type"] = navType
        }
        if let pSpanId = parentSpanId {
            commonFields["_span_parent_id"] = pSpanId
        }
        
        // Duration-based metrics are logged as spans (LCP, FCP, TTFB, INP)
        // CLS is a cumulative score, not a duration, so it's logged as a regular log
        switch name {
        case "LCP":
            handleLCPMetric(entries: entries, timestamp: timestamp, value: value, level: level, commonFields: commonFields, parentSpanId: parentSpanId)
        case "FCP":
            handleFCPMetric(entries: entries, timestamp: timestamp, value: value, level: level, commonFields: commonFields, parentSpanId: parentSpanId)
        case "TTFB":
            handleTTFBMetric(entries: entries, timestamp: timestamp, value: value, level: level, commonFields: commonFields, parentSpanId: parentSpanId)
        case "INP":
            handleINPMetric(entries: entries, timestamp: timestamp, value: value, level: level, commonFields: commonFields, parentSpanId: parentSpanId)
        case "CLS":
            handleCLSMetric(entries: entries, level: level, commonFields: commonFields)
        default:
            // Unknown metric type - log as regular log
            logger?.log(
                level: level,
                message: "webview.webVital.\(name)",
                fields: commonFields
            )
        }
    }
    
    /// Handle Largest Contentful Paint (LCP) metric.
    /// LCP measures loading performance - when the largest content element becomes visible.
    /// Logged as a span from navigation start to LCP time.
    private func handleLCPMetric(
        entries: [[String: Any]]?,
        timestamp: Double,
        value: Double,
        level: LogLevel,
        commonFields: Fields,
        parentSpanId: String?
    ) {
        var fields = commonFields
        
        // Extract LCP-specific entry data if available
        if let entry = entries?.first {
            if let element = entry["element"] as? String {
                fields["_element"] = element
            }
            if let url = entry["url"] as? String {
                fields["_url"] = url
            }
            if let size = entry["size"] as? Int {
                fields["_size"] = String(size)
            }
            if let renderTime = entry["renderTime"] as? Double {
                fields["_render_time"] = String(renderTime)
            }
            if let loadTime = entry["loadTime"] as? Double {
                fields["_load_time"] = String(loadTime)
            }
        }
        
        logDurationSpan(spanName: "webview.LCP", timestamp: timestamp, durationMs: value, level: level, fields: fields, parentSpanId: parentSpanId)
    }
    
    /// Handle First Contentful Paint (FCP) metric.
    /// FCP measures when the first content is painted to the screen.
    /// Logged as a span from navigation start to FCP time.
    private func handleFCPMetric(
        entries: [[String: Any]]?,
        timestamp: Double,
        value: Double,
        level: LogLevel,
        commonFields: Fields,
        parentSpanId: String?
    ) {
        var fields = commonFields
        
        // Extract FCP-specific entry data if available (PerformancePaintTiming)
        if let entry = entries?.first {
            if let paintType = entry["name"] as? String {
                fields["_paint_type"] = paintType
            }
            if let startTime = entry["startTime"] as? Double {
                fields["_start_time"] = String(startTime)
            }
            if let entryType = entry["entryType"] as? String {
                fields["_entry_type"] = entryType
            }
        }
        
        logDurationSpan(spanName: "webview.FCP", timestamp: timestamp, durationMs: value, level: level, fields: fields, parentSpanId: parentSpanId)
    }
    
    /// Handle Time to First Byte (TTFB) metric.
    /// TTFB measures the time from request start to receiving the first byte of the response.
    /// Logged as a span from navigation start to TTFB time.
    private func handleTTFBMetric(
        entries: [[String: Any]]?,
        timestamp: Double,
        value: Double,
        level: LogLevel,
        commonFields: Fields,
        parentSpanId: String?
    ) {
        var fields = commonFields
        
        // Extract TTFB-specific entry data if available (PerformanceNavigationTiming)
        if let entry = entries?.first {
            if let dnsStart = entry["domainLookupStart"] as? Double {
                fields["_dns_start"] = String(dnsStart)
            }
            if let dnsEnd = entry["domainLookupEnd"] as? Double {
                fields["_dns_end"] = String(dnsEnd)
            }
            if let connectStart = entry["connectStart"] as? Double {
                fields["_connect_start"] = String(connectStart)
            }
            if let connectEnd = entry["connectEnd"] as? Double {
                fields["_connect_end"] = String(connectEnd)
            }
            if let tlsStart = entry["secureConnectionStart"] as? Double {
                fields["_tls_start"] = String(tlsStart)
            }
            if let requestStart = entry["requestStart"] as? Double {
                fields["_request_start"] = String(requestStart)
            }
            if let responseStart = entry["responseStart"] as? Double {
                fields["_response_start"] = String(responseStart)
            }
        }
        
        logDurationSpan(spanName: "webview.TTFB", timestamp: timestamp, durationMs: value, level: level, fields: fields, parentSpanId: parentSpanId)
    }
    
    /// Handle Interaction to Next Paint (INP) metric.
    /// INP measures responsiveness - the time from user interaction to the next frame paint.
    /// Logged as a span representing the interaction duration.
    private func handleINPMetric(
        entries: [[String: Any]]?,
        timestamp: Double,
        value: Double,
        level: LogLevel,
        commonFields: Fields,
        parentSpanId: String?
    ) {
        var fields = commonFields
        
        // Extract INP-specific entry data if available
        if let entry = entries?.first {
            if let eventType = entry["name"] as? String {
                fields["_event_type"] = eventType
            }
            if let startTime = entry["startTime"] as? Double {
                fields["_interaction_time"] = String(startTime)
            }
            if let processingStart = entry["processingStart"] as? Double {
                fields["_processing_start"] = String(processingStart)
            }
            if let processingEnd = entry["processingEnd"] as? Double {
                fields["_processing_end"] = String(processingEnd)
            }
            if let duration = entry["duration"] as? Double {
                fields["_duration"] = String(duration)
            }
            if let interactionId = entry["interactionId"] as? Int {
                fields["_interaction_id"] = String(interactionId)
            }
        }
        
        logDurationSpan(spanName: "webview.INP", timestamp: timestamp, durationMs: value, level: level, fields: fields, parentSpanId: parentSpanId)
    }
    
    /// Handle Cumulative Layout Shift (CLS) metric.
    /// CLS measures visual stability - the sum of all unexpected layout shift scores.
    /// Unlike other metrics, CLS is a score (0-1+), not a duration, so it's logged as a regular log.
    private func handleCLSMetric(
        entries: [[String: Any]]?,
        level: LogLevel,
        commonFields: Fields
    ) {
        var fields = commonFields
        
        // Extract CLS-specific data from entries
        if let entries = entries, !entries.isEmpty {
            // Find the largest shift
            var largestShiftValue = 0.0
            var largestShiftTime = 0.0
            
            for entry in entries {
                let shiftValue = entry["value"] as? Double ?? 0.0
                if shiftValue > largestShiftValue {
                    largestShiftValue = shiftValue
                    largestShiftTime = entry["startTime"] as? Double ?? 0.0
                }
            }
            
            if largestShiftValue > 0 {
                fields["_largest_shift_value"] = String(largestShiftValue)
                fields["_largest_shift_time"] = String(largestShiftTime)
            }
            
            fields["_shift_count"] = String(entries.count)
        }
        
        logger?.log(
            level: level,
            message: "webview.CLS",
            fields: fields
        )
    }
    
    /// Log a duration-based web vital as a span with custom start/end times.
    /// The start time is calculated as (timestamp - value) where value is the duration in ms,
    /// and end time is the timestamp when the metric was reported.
    private func logDurationSpan(
        spanName: String,
        timestamp: Double,
        durationMs: Double,
        level: LogLevel,
        fields: Fields,
        parentSpanId: String?
    ) {
        // Calculate start time: the metric value represents duration from navigation start
        // timestamp is when the metric was captured (effectively the end time)
        let startTimeMs = timestamp - durationMs
        let endTimeMs = timestamp
        
        // Convert from milliseconds to TimeInterval (seconds)
        let startTimeInterval = startTimeMs / 1000.0
        let endTimeInterval = endTimeMs / 1000.0
        
        // Determine span result based on rating
        let result: SpanResult
        switch fields["_rating"] {
        case "good":
            result = .success
        case "needs-improvement", "poor":
            result = .failure
        default:
            result = .unknown
        }
        
        // Convert parentSpanId string to UUID
        let parentUUID: UUID? = parentSpanId.flatMap { UUID(uuidString: $0) }
        
        // Start span with custom start time
        let span = logger?.startSpan(
            name: spanName,
            level: level,
            file: nil,
            line: nil,
            function: nil,
            fields: fields,
            startTimeInterval: startTimeInterval,
            parentSpanID: parentUUID
        )
        
        // End span with custom end time
        span?.end(
            result,
            file: nil,
            line: nil,
            function: nil,
            fields: fields,
            endTimeInterval: endTimeInterval
        )
    }
    
    private func handleNetworkRequest(_ message: [String: Any]) {
        guard let urlString = message["url"] as? String else { return }
        
        let method = message["method"] as? String ?? "GET"
        let statusCode = message["statusCode"] as? Int ?? 0
        let durationMs = message["durationMs"] as? Int ?? 0
        let success = message["success"] as? Bool ?? false
        let error = message["error"] as? String
        let requestType = message["requestType"] as? String ?? "unknown"
        
        // Parse URL components
        let urlComponents = URLComponents(string: urlString)
        let host = urlComponents?.host
        let path = urlComponents?.path
        let query = urlComponents?.query
        
        // Build extra fields for webview context
        var extraFields: Fields = [
            "_source": "webview",
            "_request_type": requestType
        ]
        
        if let err = error {
            extraFields["_error"] = err
        }
        
        // Build metrics from timing data
        var metrics: HTTPRequestMetrics?
        if let timing = message["timing"] as? [String: Any] {
            let dnsMs = timing["dnsMs"] as? Double
            let connectMs = timing["connectMs"] as? Double
            let tlsMs = timing["tlsMs"] as? Double
            let ttfbMs = timing["ttfbMs"] as? Double
            let transferSize = timing["transferSize"] as? Int
            
            metrics = HTTPRequestMetrics(
                responseBodyBytesReceivedCount: transferSize.map { Int64($0) },
                dnsResolutionDuration: dnsMs.map { $0 / 1000.0 },
                tlsDuration: tlsMs.map { $0 / 1000.0 },
                tcpDuration: connectMs.map { $0 / 1000.0 },
                responseLatency: ttfbMs.map { $0 / 1000.0 }
            )
        }
        
        // Create request info
        let requestInfo = HTTPRequestInfo(
            method: method,
            host: host,
            path: path.map { HTTPURLPath(value: $0) },
            query: query,
            extraFields: extraFields
        )
        
        // Determine result
        let result: HTTPResponse.HTTPResult
        if !success {
            result = .failure
        } else {
            result = .success
        }
        
        // Create response
        let response = HTTPResponse(
            result: result,
            statusCode: statusCode > 0 ? statusCode : nil,
            error: nil
        )
        
        // Create response info
        let responseInfo = HTTPResponseInfo(
            requestInfo: requestInfo,
            response: response,
            duration: TimeInterval(durationMs) / 1000.0,
            metrics: metrics,
            extraFields: extraFields
        )
        
        // Log using native HTTP logging
        logger?.log(requestInfo, file: nil, line: nil, function: nil)
        logger?.log(responseInfo, file: nil, line: nil, function: nil)
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
    
    /// Handle page view span start/end messages.
    /// Page view spans group all events within a single page session.
    private func handlePageView(_ message: [String: Any]) {
        guard let action = message["action"] as? String,
              let spanId = message["spanId"] as? String else { return }
        
        let url = message["url"] as? String ?? ""
        let reason = message["reason"] as? String ?? ""
        let timestamp = message["timestamp"] as? Double ?? (Date().timeIntervalSince1970 * 1000)
        let timestampInterval = timestamp / 1000.0
        
        switch action {
        case "start":
            currentPageSpanId = spanId
            
            let fields: Fields = [
                "_span_id": spanId,
                "_url": url,
                "_reason": reason,
                "_source": "webview"
            ]
            
            // Start the page view span (include URL in name for visibility)
            if let span = logger?.startSpan(
                name: "webview.pageView: \(url)",
                level: .debug,
                file: nil,
                line: nil,
                function: nil,
                fields: fields,
                startTimeInterval: timestampInterval,
                parentSpanID: nil
            ) {
                activePageViewSpans[spanId] = span
            }
            
        case "end":
            let durationMs = message["durationMs"] as? Double
            
            var fields: Fields = [
                "_span_id": spanId,
                "_url": url,
                "_reason": reason,
                "_source": "webview"
            ]
            
            if let duration = durationMs {
                fields["_duration_ms"] = String(duration)
            }
            
            // End the page view span
            if let span = activePageViewSpans.removeValue(forKey: spanId) {
                span.end(
                    .success,
                    file: nil,
                    line: nil,
                    function: nil,
                    fields: fields,
                    endTimeInterval: timestampInterval
                )
            }
            
            // Clear current page span ID if it matches
            if currentPageSpanId == spanId {
                currentPageSpanId = nil
            }
            
        default:
            break
        }
    }
    
    /// Handle lifecycle events (DOMContentLoaded, load, visibilitychange).
    /// These are markers within the page view span.
    private func handleLifecycle(_ message: [String: Any]) {
        guard let event = message["event"] as? String else { return }
        
        let parentSpanId = message["parentSpanId"] as? String ?? currentPageSpanId
        let performanceTime = message["performanceTime"] as? Double
        let visibilityState = message["visibilityState"] as? String
        
        var fields: Fields = [
            "_event": event,
            "_source": "webview"
        ]
        
        if let pSpanId = parentSpanId {
            fields["_span_parent_id"] = pSpanId
        }
        if let perfTime = performanceTime {
            fields["_performance_time"] = String(perfTime)
        }
        if let visState = visibilityState {
            fields["_visibility_state"] = visState
        }
        
        logger?.log(
            level: .debug,
            message: "webview.lifecycle.\(event)",
            fields: fields
        )
    }
    
    private func handleError(_ message: [String: Any]) {
        let name = message["name"] as? String ?? "Error"
        let errorMessage = message["message"] as? String ?? "Unknown error"
        let stack = message["stack"] as? String
        let filename = message["filename"] as? String
        let lineno = message["lineno"] as? Int
        let colno = message["colno"] as? Int
        let parentSpanId = message["parentSpanId"] as? String ?? currentPageSpanId
        
        var fields: Fields = [
            "_name": name,
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
        if let pSpanId = parentSpanId {
            fields["_span_parent_id"] = pSpanId
        }
        
        logger?.log(
            level: .error,
            message: "webview.error",
            fields: fields
        )
    }
    
    /// Handle long task events (main thread blocked > 50ms).
    private func handleLongTask(_ message: [String: Any]) {
        guard let durationMs = message["durationMs"] as? Double else { return }
        
        let startTime = message["startTime"] as? Double
        let parentSpanId = message["parentSpanId"] as? String ?? currentPageSpanId
        let attribution = message["attribution"] as? [String: Any]
        
        var fields: Fields = [
            "_duration_ms": String(durationMs),
            "_source": "webview"
        ]
        
        if let st = startTime {
            fields["_start_time"] = String(st)
        }
        if let pSpanId = parentSpanId {
            fields["_span_parent_id"] = pSpanId
        }
        
        // Extract attribution data
        if let attr = attribution {
            if let name = attr["name"] as? String {
                fields["_attribution_name"] = name
            }
            if let containerType = attr["containerType"] as? String {
                fields["_container_type"] = containerType
            }
            if let containerSrc = attr["containerSrc"] as? String {
                fields["_container_src"] = containerSrc
            }
            if let containerId = attr["containerId"] as? String {
                fields["_container_id"] = containerId
            }
            if let containerName = attr["containerName"] as? String {
                fields["_container_name"] = containerName
            }
        }
        
        // Determine log level based on duration
        let level: LogLevel
        if durationMs >= 200 {
            level = .warning
        } else if durationMs >= 100 {
            level = .info
        } else {
            level = .debug
        }
        
        logger?.log(
            level: level,
            message: "webview.longTask",
            fields: fields
        )
    }
    
    /// Handle resource loading failures (images, scripts, stylesheets, etc.).
    private func handleResourceError(_ message: [String: Any]) {
        let resourceType = message["resourceType"] as? String ?? "unknown"
        let url = message["url"] as? String ?? ""
        let tagName = message["tagName"] as? String ?? ""
        let parentSpanId = message["parentSpanId"] as? String ?? currentPageSpanId
        
        var fields: Fields = [
            "_resource_type": resourceType,
            "_url": url,
            "_tag_name": tagName,
            "_source": "webview"
        ]
        
        if let pSpanId = parentSpanId {
            fields["_span_parent_id"] = pSpanId
        }
        
        logger?.log(
            level: .warning,
            message: "webview.resourceError",
            fields: fields
        )
    }
    
    /// Handle console messages (log, warn, error, info, debug).
    private func handleConsole(_ message: [String: Any]) {
        let consoleLevel = message["level"] as? String ?? "log"
        let consoleMessage = message["message"] as? String ?? ""
        let parentSpanId = message["parentSpanId"] as? String ?? currentPageSpanId
        
        var fields: Fields = [
            "_level": consoleLevel,
            "_message": String(consoleMessage.prefix(500)),
            "_source": "webview"
        ]
        
        if let pSpanId = parentSpanId {
            fields["_span_parent_id"] = pSpanId
        }
        
        // Extract additional args if present
        if let args = message["args"] as? [String], !args.isEmpty {
            let argsStr = args.prefix(5).joined(separator: ", ")
            fields["_args"] = String(argsStr.prefix(500))
        }
        
        // Map console level to LogLevel
        let level: LogLevel
        switch consoleLevel {
        case "error":
            level = .error
        case "warn":
            level = .warning
        case "info":
            level = .info
        default:
            level = .debug
        }
        
        logger?.log(
            level: level,
            message: "webview.console.\(consoleLevel)",
            fields: fields
        )
    }
    
    /// Handle unhandled promise rejections.
    private func handlePromiseRejection(_ message: [String: Any]) {
        let reason = message["reason"] as? String ?? "Unknown rejection"
        let stack = message["stack"] as? String
        let parentSpanId = message["parentSpanId"] as? String ?? currentPageSpanId
        
        var fields: Fields = [
            "_reason": reason,
            "_source": "webview"
        ]
        
        if let s = stack {
            fields["_stack"] = String(s.prefix(1000))
        }
        if let pSpanId = parentSpanId {
            fields["_span_parent_id"] = pSpanId
        }
        
        logger?.log(
            level: .error,
            message: "webview.promiseRejection",
            fields: fields
        )
    }
    
    /// Handle user interaction events (clicks and rage clicks).
    private func handleUserInteraction(_ message: [String: Any]) {
        guard let interactionType = message["interactionType"] as? String else { return }
        
        let tagName = message["tagName"] as? String ?? ""
        let elementId = message["elementId"] as? String
        let className = message["className"] as? String
        let textContent = message["textContent"] as? String
        let isClickable = message["isClickable"] as? Bool ?? false
        let clickCount = message["clickCount"] as? Int
        let timeWindowMs = message["timeWindowMs"] as? Int
        let parentSpanId = message["parentSpanId"] as? String ?? currentPageSpanId
        
        var fields: Fields = [
            "_interaction_type": interactionType,
            "_tag_name": tagName,
            "_is_clickable": String(isClickable),
            "_source": "webview"
        ]
        
        if let elId = elementId {
            fields["_element_id"] = elId
        }
        if let clsName = className {
            fields["_class_name"] = String(clsName.prefix(100))
        }
        if let txt = textContent {
            fields["_text_content"] = String(txt.prefix(50))
        }
        if let cc = clickCount {
            fields["_click_count"] = String(cc)
        }
        if let tw = timeWindowMs {
            fields["_time_window_ms"] = String(tw)
        }
        if let pSpanId = parentSpanId {
            fields["_span_parent_id"] = pSpanId
        }
        
        // Rage clicks are more important
        let level: LogLevel = interactionType == "rageClick" ? .warning : .debug
        
        logger?.log(
            level: level,
            message: "webview.userInteraction.\(interactionType)",
            fields: fields
        )
    }
}
