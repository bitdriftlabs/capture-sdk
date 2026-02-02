// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { safeCall } from './safe-call';
import type {
    AnyBridgeMessage,
    AnyBridgeMessageMap,
    BridgeMessage,
    BridgeReadyMessage,
    ConsoleMessage,
    CustomLogMessage,
    ErrorMessage,
    InternalAutoInstrumentationMessage,
    LifecycleMessage,
    LongTaskMessage,
    NavigationMessage,
    PageViewMessage,
    PromiseRejectionMessage,
    ResourceErrorMessage,
    SerializableLogFields,
    UserInteractionMessage,
    WebVitalMessage,
} from './types';

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
 * Uses type parameter to infer the correct data shape
 */
const buildFieldsForMessage = <T extends keyof AnyBridgeMessageMap>(
    type: T,
    data: Omit<AnyBridgeMessageMap[T], 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>
): SerializableLogFields => {
    const fields: Record<string, string> = {};
    
    switch (type) {
        case 'navigation': {
            const navData = data as Omit<NavigationMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._from_url = String(navData.fromUrl ?? '');
            fields._to_url = String(navData.toUrl ?? '');
            fields._method = String(navData.method ?? '');
            break;
        }
            
        case 'error': {
            const errorData = data as Omit<ErrorMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._name = String(errorData.name ?? 'Error');
            fields._message = String(errorData.message ?? 'Unknown error');
            if (errorData.stack) fields._stack = String(errorData.stack);
            if (errorData.filename) fields._filename = String(errorData.filename);
            if (errorData.lineno) fields._lineno = String(errorData.lineno);
            if (errorData.colno) fields._colno = String(errorData.colno);
            break;
        }
            
        case 'promiseRejection': {
            const rejectionData = data as Omit<PromiseRejectionMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._reason = String(rejectionData.reason ?? 'Unknown rejection');
            if (rejectionData.stack) fields._stack = String(rejectionData.stack);
            break;
        }
            
        case 'longTask': {
            const taskData = data as Omit<LongTaskMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._duration_ms = String(taskData.durationMs ?? 0);
            fields._start_time = String(taskData.startTime ?? 0);
            if (taskData.attribution && typeof taskData.attribution === 'object') {
                Object.assign(fields, flattenObject(taskData.attribution as Record<string, unknown>, '_attribution'));
            }
            break;
        }
            
        case 'pageView': {
            const pageData = data as Omit<PageViewMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._span_id = String(pageData.spanId ?? '');
            fields._url = String(pageData.url ?? '');
            fields._reason = String(pageData.reason ?? '');
            if (pageData.durationMs !== undefined) {
                fields._duration_ms = String(pageData.durationMs);
            }
            break;
        }
            
        case 'lifecycle': {
            const lifecycleData = data as Omit<LifecycleMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._event = String(lifecycleData.event ?? '');
            if (lifecycleData.performanceTime !== undefined) {
                fields._performance_time = String(lifecycleData.performanceTime);
            }
            if (lifecycleData.visibilityState) {
                fields._visibility_state = String(lifecycleData.visibilityState);
            }
            break;
        }
            
        case 'console': {
            const consoleData = data as Omit<ConsoleMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._level = String(consoleData.level ?? 'log');
            fields._message = String(consoleData.message ?? '');
            if (consoleData.args && Array.isArray(consoleData.args)) {
                if (consoleData.args.length > 0) {
                    fields._args = JSON.stringify(consoleData.args.slice(0, 5));
                }
            }
            break;
        }
            
        case 'resourceError': {
            const resourceData = data as Omit<ResourceErrorMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._resource_type = String(resourceData.resourceType ?? 'unknown');
            fields._url = String(resourceData.url ?? '');
            fields._tag_name = String(resourceData.tagName ?? '');
            break;
        }
            
        case 'userInteraction': {
            const interactionData = data as Omit<UserInteractionMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._interaction_type = String(interactionData.interactionType ?? '');
            fields._tag_name = String(interactionData.tagName ?? '');
            fields._is_clickable = String(interactionData.isClickable ?? false);
            if (interactionData.elementId) fields._element_id = String(interactionData.elementId);
            if (interactionData.className) fields._class_name = String(interactionData.className);
            if (interactionData.textContent) fields._text_content = String(interactionData.textContent);
            if (interactionData.clickCount !== undefined) fields._click_count = String(interactionData.clickCount);
            if (interactionData.timeWindowMs !== undefined) fields._time_window_ms = String(interactionData.timeWindowMs);
            if (interactionData.duration !== undefined) fields._duration = String(interactionData.duration);
            break;
        }
            
        case 'bridgeReady': {
            const readyData = data as Omit<BridgeReadyMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._url = String(readyData.url ?? '');
            if (readyData.instrumentationConfig) {
                fields._config = JSON.stringify(readyData.instrumentationConfig);
            }
            break;
        }
            
        case 'internalAutoInstrumentation': {
            const autoData = data as Omit<InternalAutoInstrumentationMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            fields._event = String(autoData.event ?? '');
            break;
        }
            
        case 'webVital': {
            const vitalData = data as Omit<WebVitalMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            // For webVital messages, extract fields from the metric object
            const metric = vitalData.metric;
            if (metric) {
                fields._metric = String(metric.name ?? '');
                fields._value = String(metric.value ?? '');
                fields._rating = String(metric.rating ?? '');
                
                if (metric.delta !== undefined) fields._delta = String(metric.delta);
                if (metric.id) fields._metric_id = String(metric.id);
                if (metric.navigationType) fields._navigation_type = String(metric.navigationType);
                if (vitalData.parentSpanId) fields._span_parent_id = String(vitalData.parentSpanId);
                
                // Extract all relevant entry fields based on metric type
                const entries = metric.entries;
                const entry = entries?.[0];
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
                if (metric.name === 'CLS' && entries && entries.length > 0) {
                    let largestShiftValue = 0;
                    let largestShiftTime = 0;
                    
                    for (const clsEntry of entries) {
                        const shiftValue = 'value' in clsEntry ? Number(clsEntry.value) : 0;
                        if (shiftValue > largestShiftValue) {
                            largestShiftValue = shiftValue;
                            largestShiftTime = Number(clsEntry.startTime) ?? 0;
                        }
                    }
                    
                    if (largestShiftValue > 0) {
                        fields._largest_shift_value = String(largestShiftValue);
                        fields._largest_shift_time = String(largestShiftTime);
                    }
                    fields._shift_count = String(entries.length);
                }
            }
            break;
        }
            
        case 'customLog': {
            const customData = data as Omit<CustomLogMessage, 'v' | 'timestamp' | 'tag' | 'type' | 'fields'>;
            // For custom logs, use the provided fields directly
            if (customData.fields && typeof customData.fields === 'object') {
                Object.assign(fields, customData.fields);
            }
            break;
        }
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
    
    // Build fields from the message data with proper typing
    const messageFields = buildFieldsForMessage(type, partial);
    
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
