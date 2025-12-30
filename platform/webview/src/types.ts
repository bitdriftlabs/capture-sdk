// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { type MetricType } from 'web-vitals';

/**
 * Message types sent from JS to native bridge
 */
export type MessageType =
    | 'bridgeReady'
    | 'webVital'
    | 'networkRequest'
    | 'navigation'
    | 'pageView'
    | 'lifecycle'
    | 'error'
    | 'longTask'
    | 'resourceError'
    | 'console'
    | 'promiseRejection'
    | 'userInteraction';

/**
 * Base interface for all bridge messages
 */
export interface BridgeMessage {
    tag: 'bitdrift-webview-sdk';
    /** Protocol version for forward compatibility */
    v: 1;
    /** Message type discriminator */
    type: MessageType;
    /** Timestamp when the event occurred (ms since epoch) */
    timestamp: number;
}

/**
 * Sent immediately when the bridge is initialized
 */
export interface BridgeReadyMessage extends BridgeMessage {
    type: 'bridgeReady';
    /** URL of the page being loaded */
    url: string;
}

/**
 * Core Web Vitals and other performance metrics
 */
export interface WebVitalMessage extends BridgeMessage {
    type: 'webVital';
    metric: MetricType;
    /** Parent span ID for nesting under page view */
    parentSpanId?: string;
}

/**
 * Network request captured via fetch/XHR interception
 */
export interface NetworkRequestMessage extends BridgeMessage {
    type: 'networkRequest';
    /** Unique identifier for correlating start/end */
    requestId: string;
    /** HTTP method */
    method: string;
    /** Request URL */
    url: string;
    /** HTTP status code (0 if request failed) */
    statusCode: number;
    /** Request duration in milliseconds */
    durationMs: number;
    /** Whether the request succeeded */
    success: boolean;
    /** Error message if request failed */
    error?: string;
    /** Request type: 'fetch' | 'xhr' */
    requestType: PerformanceResourceTiming['initiatorType'];
    /** Resource timing data if available */
    timing?: PerformanceResourceTiming;
}

/**
 * Detailed timing from Resource Timing API
 */
export interface ResourceTimingData {
    /** DNS lookup time */
    dnsMs?: number;
    /** TCP connection time */
    connectMs?: number;
    /** TLS handshake time */
    tlsMs?: number;
    /** Time to first byte */
    ttfbMs?: number;
    /** Response download time */
    downloadMs?: number;
    /** Total transfer size in bytes */
    transferSize?: number;
}

/**
 * SPA navigation event via History API
 */
export interface NavigationMessage extends BridgeMessage {
    type: 'navigation';
    /** Previous URL */
    fromUrl: string;
    /** New URL */
    toUrl: string;
    /** Navigation method: 'pushState' | 'replaceState' | 'popstate' */
    method: 'pushState' | 'replaceState' | 'popstate';
}

/**
 * JavaScript error captured
 */
export interface ErrorMessage extends BridgeMessage {
    type: 'error';
    /** Error name (e.g., "TypeError", "ReferenceError") */
    name: string;
    /** Error message */
    message: string;
    /** Stack trace if available */
    stack?: string;
    /** Source file */
    filename?: string;
    /** Line number */
    lineno?: number;
    /** Column number */
    colno?: number;
}

/**
 * Page view span for grouping events within a page session
 */
export interface PageViewMessage extends BridgeMessage {
    type: 'pageView';
    /** Action: start or end of page view */
    action: 'start' | 'end';
    /** Unique span ID for this page view */
    spanId: string;
    /** URL of the page */
    url: string;
    /** Reason for the page view event */
    reason: 'initial' | 'navigation' | 'unload' | 'hidden';
    /** Duration of page view in ms (only on end) */
    durationMs?: number;
}

/**
 * Lifecycle events within a page view
 */
export interface LifecycleMessage extends BridgeMessage {
    type: 'lifecycle';
    /** Lifecycle event type */
    event: 'DOMContentLoaded' | 'load' | 'visibilitychange';
    /** Performance time when event occurred */
    performanceTime: number;
    /** Visibility state (for visibilitychange) */
    visibilityState?: string;
}

/**
 * Long task detected (blocking main thread > 50ms)
 */
export interface LongTaskMessage extends BridgeMessage {
    type: 'longTask';
    /** Duration of the long task in ms */
    durationMs: number;
    /** Start time relative to navigation start */
    startTime: number;
    /** Attribution data for the long task */
    attribution?: {
        name?: string;
        containerType?: string;
        containerSrc?: string;
        containerId?: string;
        containerName?: string;
    };
}

/**
 * Resource loading failure (images, scripts, stylesheets, etc.)
 */
export interface ResourceErrorMessage extends BridgeMessage {
    type: 'resourceError';
    /** Type of resource that failed */
    resourceType: string;
    /** URL of the failed resource */
    url: string;
    /** Tag name of the element */
    tagName: string;
}

/**
 * Console message captured
 */
export interface ConsoleMessage extends BridgeMessage {
    type: 'console';
    /** Console method: log, warn, error, info, debug */
    level: 'log' | 'warn' | 'error' | 'info' | 'debug';
    /** Console message content */
    message: string;
    /** Additional arguments passed to console */
    args?: string[];
}

/**
 * Unhandled promise rejection
 */
export interface PromiseRejectionMessage extends BridgeMessage {
    type: 'promiseRejection';
    /** Rejection reason/message */
    reason: string;
    /** Stack trace if available */
    stack?: string;
}

/**
 * User interaction event
 */
export interface UserInteractionMessage extends BridgeMessage {
    type: 'userInteraction';
    /** Interaction type */
    interactionType: 'click' | 'rageClick';
    /** Target element tag name */
    tagName: string;
    /** Target element ID if present */
    elementId?: string;
    /** Target element classes */
    className?: string;
    /** Text content (truncated) */
    textContent?: string;
    /** Whether the element is typically clickable */
    isClickable: boolean;
    /** Click count (for rage clicks) */
    clickCount?: number;
    /** Time window for rage clicks in ms */
    timeWindowMs?: number;
}

/**
 * Union type of all possible messages
 */
export type AnyBridgeMessage =
    | BridgeReadyMessage
    | WebVitalMessage
    | NetworkRequestMessage
    | NavigationMessage
    | PageViewMessage
    | LifecycleMessage
    | ErrorMessage
    | LongTaskMessage
    | ResourceErrorMessage
    | ConsoleMessage
    | PromiseRejectionMessage
    | UserInteractionMessage;
