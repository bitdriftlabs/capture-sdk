// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/**
 * Message types sent from JS to native bridge
 */
export type MessageType = 
  | 'bridgeReady'
  | 'webVital'
  | 'networkRequest'
  | 'navigation'
  | 'error';

/**
 * Base interface for all bridge messages
 */
export interface BridgeMessage {
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
  /** Metric name: LCP, CLS, INP, FCP, TTFB */
  name: string;
  /** Metric value (ms for timing metrics, unitless for CLS) */
  value: number;
  /** Rating: 'good' | 'needs-improvement' | 'poor' */
  rating: string;
  /** Navigation type that triggered this metric */
  navigationType?: string;
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
  requestType: 'fetch' | 'xhr';
  /** Resource timing data if available */
  timing?: ResourceTimingData;
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
 * Union type of all possible messages
 */
export type AnyBridgeMessage = 
  | BridgeReadyMessage
  | WebVitalMessage
  | NetworkRequestMessage
  | NavigationMessage
  | ErrorMessage;
