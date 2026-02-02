// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { safeCall } from './safe-call';
import type { AnyBridgeMessage, AnyBridgeMessageMap, BridgeMessage, CustomLogMessage, SerializableLogFields } from './types';

export const pristine = {
    console: {
        log: console.log,
        warn: console.warn,
        error: console.error,
        info: console.info,
        debug: console.debug,
    },
};

/**
 * Create a fields object with standard fields that are always included:
 * - _source: "webview"
 * - _timestamp: current timestamp in ms
 */
export const createStandardFields = (timestamp?: number): { _source: string; _timestamp: string } => {
    return {
        _source: 'webview',
        _timestamp: (timestamp ?? Date.now()).toString(),
    };
};

/**
 * Flatten nested objects with underscore-prefixed keys.
 * E.g., { attribution: { name: 'foo', containerType: 'bar' } }
 * becomes { _attribution.name: 'foo', _attribution.container_type: 'bar' }
 */
export const flattenObject = (
    obj: Record<string, unknown>,
    prefix = '',
): Record<string, string> => {
    const result: Record<string, string> = {};
    
    for (const [key, value] of Object.entries(obj)) {
        if (value === null || value === undefined) {
            continue;
        }
        
        // Convert camelCase to snake_case
        const snakeKey = key.replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`);
        // Build the full field key - use dot notation for nested keys
        // When there's a prefix, remove the leading underscore from snakeKey if present
        const snakeKeyWithoutLeadingUnderscore = prefix && snakeKey.startsWith('_') ? snakeKey.substring(1) : snakeKey;
        const fieldKey = prefix ? `${prefix}.${snakeKeyWithoutLeadingUnderscore}` : `_${snakeKey}`;
        
        if (typeof value === 'object' && !Array.isArray(value)) {
            // Recursively flatten nested objects
            // For nested objects, pass the current fieldKey (without dot) as prefix
            Object.assign(result, flattenObject(value as Record<string, unknown>, fieldKey));
        } else {
            // Convert value to string for logging
            result[fieldKey] = value.toString();
        }
    }
    
    return result;
};

type Platform = 'ios' | 'android' | 'unknown';

const detectPlatform = (): Platform => {
    return (
        safeCall(() => {
            if (window.webkit?.messageHandlers?.BitdriftLogger) {
                return 'ios';
            }
            if (window.BitdriftLogger) {
                return 'android';
            }
            return 'unknown';
        }) ?? 'unknown'
    );
};

const sendToNative = (message: AnyBridgeMessage): void => {
    safeCall(() => {
        const platform = detectPlatform();
        const serialized = JSON.stringify(message);

        switch (platform) {
            case 'ios':
                window.webkit?.messageHandlers?.BitdriftLogger?.postMessage(message);
                break;
            case 'android':
                window.BitdriftLogger?.log(serialized);
                break;
            case 'unknown':
                // In development/testing, log to console
                if (typeof console !== 'undefined') {
                    console.debug('[Bitdrift WebView]', message);
                }
                break;
        }
    });
};

/**
 * Initialize the global bitdrift object
 */
export const initBridge = (): void => {
    // Avoid re-initialization
    if (window.bitdrift?.log) {
        return;
    }

    window.bitdrift = {
        config: window.bitdrift?.config,
        log: (
            ...args:
                | [AnyBridgeMessage]
                | [CustomLogMessage['level'], CustomLogMessage['message'], CustomLogMessage['fields']]
        ): void => {
            if (args.length !== 1 && args.length !== 3) {
                throw new Error('Invalid arguments to bitdrift.log. Expected 1 or 3 arguments.');
            }

            let message: AnyBridgeMessage;
            if (args.length === 1) {
                message = args[0];
            } else {
                const [level, msg, fields] = args;
                message = createMessage({
                    type: 'customLog',
                    level,
                    message: msg,
                    fields,
                });
            }
            sendToNative(message);
        },
    };
};

/**
 * Send a message through the bridge
 */
export const log = (message: AnyBridgeMessage): void => {
    if (window.bitdrift) {
        window.bitdrift.log?.(message);
    } else {
        sendToNative(message);
    }
};

/**
 * Helper to create fields from message data based on message type
 */
const buildFieldsForMessage = (type: string, data: Record<string, unknown>): SerializableLogFields => {
    const fields: Record<string, string> = {};
    
    switch (type) {
        case 'navigation':
            fields._from_url = String(data.fromUrl ?? '');
            fields._to_url = String(data.toUrl ?? '');
            fields._method = String(data.method ?? '');
            break;
            
        case 'error':
            fields._name = String(data.name ?? 'Error');
            fields._message = String(data.message ?? 'Unknown error');
            if (data.stack) fields._stack = String(data.stack);
            if (data.filename) fields._filename = String(data.filename);
            if (data.lineno) fields._lineno = String(data.lineno);
            if (data.colno) fields._colno = String(data.colno);
            break;
            
        case 'promiseRejection':
            fields._reason = String(data.reason ?? 'Unknown rejection');
            if (data.stack) fields._stack = String(data.stack);
            break;
            
        case 'longTask':
            fields._duration_ms = String(data.durationMs ?? 0);
            fields._start_time = String(data.startTime ?? 0);
            if (data.attribution && typeof data.attribution === 'object') {
                Object.assign(fields, flattenObject(data.attribution as Record<string, unknown>, '_attribution'));
            }
            break;
            
        case 'pageView':
            fields._span_id = String(data.spanId ?? '');
            fields._url = String(data.url ?? '');
            fields._reason = String(data.reason ?? '');
            if (data.durationMs !== undefined) {
                fields._duration_ms = String(data.durationMs);
            }
            break;
            
        case 'lifecycle':
            fields._event = String(data.event ?? '');
            if (data.performanceTime !== undefined) {
                fields._performance_time = String(data.performanceTime);
            }
            if (data.visibilityState) {
                fields._visibility_state = String(data.visibilityState);
            }
            break;
            
        case 'console':
            fields._level = String(data.level ?? 'log');
            fields._message = String(data.message ?? '');
            if (data.args && Array.isArray(data.args)) {
                const args = data.args as string[];
                if (args.length > 0) {
                    fields._args = JSON.stringify(args.slice(0, 5));
                }
            }
            break;
            
        case 'resourceError':
            fields._resource_type = String(data.resourceType ?? 'unknown');
            fields._url = String(data.url ?? '');
            fields._tag_name = String(data.tagName ?? '');
            break;
            
        case 'userInteraction':
            fields._interaction_type = String(data.interactionType ?? '');
            fields._tag_name = String(data.tagName ?? '');
            fields._is_clickable = String(data.isClickable ?? false);
            if (data.elementId) fields._element_id = String(data.elementId);
            if (data.className) fields._class_name = String(data.className);
            if (data.textContent) fields._text_content = String(data.textContent);
            if (data.clickCount !== undefined) fields._click_count = String(data.clickCount);
            if (data.timeWindowMs !== undefined) fields._time_window_ms = String(data.timeWindowMs);
            if (data.duration !== undefined) fields._duration = String(data.duration);
            break;
            
        case 'bridgeReady':
            fields._url = String(data.url ?? '');
            if (data.instrumentationConfig) {
                fields._config = JSON.stringify(data.instrumentationConfig);
            }
            break;
            
        case 'internalAutoInstrumentation':
            fields._event = String(data.event ?? '');
            break;
            
        case 'webVital': {
            // For webVital messages, extract fields from the metric object
            const metric = data.metric as any;
            if (metric) {
                fields._metric = String(metric.name ?? '');
                fields._value = String(metric.value ?? '');
                fields._rating = String(metric.rating ?? '');
                
                if (metric.delta !== undefined) fields._delta = String(metric.delta);
                if (metric.id) fields._metric_id = String(metric.id);
                if (metric.navigationType) fields._navigation_type = String(metric.navigationType);
                if (data.parentSpanId) fields._span_parent_id = String(data.parentSpanId);
                
                // Extract all relevant entry fields based on metric type
                const entry = metric.entries?.[0];
                if (entry) {
                    // Common fields
                    if (entry.startTime !== undefined) fields._start_time = String(entry.startTime);
                    if (entry.entryType) fields._entry_type = String(entry.entryType);
                    
                    // LCP-specific fields
                    if ('element' in entry && entry.element) {
                        const element = entry.element as Element;
                        const elementStr = element.tagName ? 
                            `${element.tagName.toLowerCase()}${element.id ? '#' + element.id : ''}${element.className ? '.' + element.className.split(' ').join('.') : ''}` :
                            element.toString();
                        fields._element = elementStr;
                    }
                    if ('url' in entry && entry.url) fields._url = String(entry.url);
                    if ('size' in entry && entry.size !== undefined) fields._size = String(entry.size);
                    if ('renderTime' in entry && entry.renderTime !== undefined) fields._render_time = String(entry.renderTime);
                    if ('loadTime' in entry && entry.loadTime !== undefined) fields._load_time = String(entry.loadTime);
                    
                    // FCP-specific fields
                    if ('name' in entry && entry.name && metric.name === 'FCP') fields._paint_type = String(entry.name);
                    
                    // TTFB-specific fields (PerformanceNavigationTiming)
                    if ('domainLookupStart' in entry && entry.domainLookupStart !== undefined) fields._dns_start = String(entry.domainLookupStart);
                    if ('domainLookupEnd' in entry && entry.domainLookupEnd !== undefined) fields._dns_end = String(entry.domainLookupEnd);
                    if ('connectStart' in entry && entry.connectStart !== undefined) fields._connect_start = String(entry.connectStart);
                    if ('connectEnd' in entry && entry.connectEnd !== undefined) fields._connect_end = String(entry.connectEnd);
                    if ('secureConnectionStart' in entry && entry.secureConnectionStart !== undefined) fields._tls_start = String(entry.secureConnectionStart);
                    if ('requestStart' in entry && entry.requestStart !== undefined) fields._request_start = String(entry.requestStart);
                    if ('responseStart' in entry && entry.responseStart !== undefined) fields._response_start = String(entry.responseStart);
                    
                    // INP-specific fields
                    if ('processingStart' in entry && entry.processingStart !== undefined) fields._processing_start = String(entry.processingStart);
                    if ('processingEnd' in entry && entry.processingEnd !== undefined) fields._processing_end = String(entry.processingEnd);
                    if ('duration' in entry && entry.duration !== undefined) fields._duration = String(entry.duration);
                    if ('interactionId' in entry && entry.interactionId !== undefined) fields._interaction_id = String(entry.interactionId);
                    if ('name' in entry && entry.name && metric.name === 'INP') fields._event_type = String(entry.name);
                }
                
                // CLS-specific: Extract all entries for layout shift data
                if (metric.name === 'CLS' && metric.entries && metric.entries.length > 0) {
                    let largestShiftValue = 0;
                    let largestShiftTime = 0;
                    
                    for (const clsEntry of metric.entries) {
                        const shiftValue = 'value' in clsEntry ? (clsEntry.value as number) : 0;
                        if (shiftValue > largestShiftValue) {
                            largestShiftValue = shiftValue;
                            largestShiftTime = clsEntry.startTime ?? 0;
                        }
                    }
                    
                    if (largestShiftValue > 0) {
                        fields._largest_shift_value = String(largestShiftValue);
                        fields._largest_shift_time = String(largestShiftTime);
                    }
                    fields._shift_count = String(metric.entries.length);
                }
            }
            break;
        }
            
        case 'customLog':
            // For custom logs, use the provided fields directly
            if (data.fields && typeof data.fields === 'object') {
                Object.assign(fields, data.fields);
            }
            break;
    }
    
    return fields;
};

/**
 * Helper to create a timestamped message with standard fields automatically added
 */
export const createMessage = <T extends keyof AnyBridgeMessageMap>({
    type,
    timestamp,
    ...partial
}: { type: T; timestamp?: number } & Omit<
    AnyBridgeMessageMap[T],
    'v' | 'timestamp' | 'tag' | 'type' | 'fields'
>): AnyBridgeMessageMap[T] => {
    const ts = timestamp ?? Date.now();
    const standardFields = createStandardFields(ts);
    
    // Build fields from the message data
    const messageFields = buildFieldsForMessage(type, partial as Record<string, unknown>);
    
    return {
        tag: 'bitdrift-webview-sdk',
        v: 1,
        timestamp: ts,
        type,
        ...partial,
        // Merge standard fields with message-specific fields
        fields: { ...standardFields, ...messageFields },
    } as unknown as AnyBridgeMessageMap[T];
};

export const isAnyBridgeMessage = (obj: unknown): obj is AnyBridgeMessage => {
    return (
        typeof obj === 'object' &&
        obj !== null &&
        'type' in obj &&
        typeof (obj as BridgeMessage).type === 'string' &&
        'v' in obj &&
        typeof (obj as BridgeMessage).v === 'number' &&
        'tag' in obj &&
        (obj as BridgeMessage).tag === 'bitdrift-webview-sdk'
    );
};
