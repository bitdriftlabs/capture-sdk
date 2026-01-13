// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import type { NetworkRequestMessage } from './types';

let requestCounter = 0;

const generateRequestId = (): string => {
    return `req_${Date.now()}_${++requestCounter}`;
};

/**
 * Track JS-initiated requests to avoid double-logging when PerformanceObserver fires.
 * Maps URL to the timestamp when the request completed.
 */
const jsInitiatedRequests = new Map<string, number>();

/**
 * How long to keep a URL in the deduplication set (ms).
 * PerformanceObserver entries typically arrive within a few hundred ms of completion.
 */
const DEDUP_WINDOW_MS = 5000;

/**
 * Mark a URL as having been captured via JS interception.
 * This prevents the PerformanceObserver from double-logging it.
 */
const markAsJsInitiated = (url: string): void => {
    jsInitiatedRequests.set(url, Date.now());

    // Periodic cleanup of old entries to prevent memory growth
    if (jsInitiatedRequests.size > 100) {
        const now = Date.now();
        for (const [key, timestamp] of jsInitiatedRequests.entries()) {
            if (now - timestamp > DEDUP_WINDOW_MS) {
                jsInitiatedRequests.delete(key);
            }
        }
    }
};

/**
 * Check if a URL was recently captured via JS interception.
 * Returns true and removes the entry if found (allowing future requests to same URL).
 */
const wasJsInitiated = (url: string, entryEndTime: number): boolean => {
    const timestamp = jsInitiatedRequests.get(url);
    if (timestamp === undefined) {
        return false;
    }

    // Check if the entry is within our dedup window
    // entryEndTime is relative to performance.timeOrigin, convert to wall clock
    const entryWallTime = performance.timeOrigin + entryEndTime;
    const timeDiff = Math.abs(entryWallTime - timestamp);

    if (timeDiff < DEDUP_WINDOW_MS) {
        // This is likely the same request we already logged
        jsInitiatedRequests.delete(url);
        return true;
    }

    return false;
};

/**
 * Try to get resource timing data for a URL
 */
const getResourceTiming = (url: string): PerformanceResourceTiming | undefined => {
    if (typeof performance === 'undefined' || !performance.getEntriesByType) {
        return undefined;
    }

    const entries = performance.getEntriesByType('resource') as PerformanceResourceTiming[];
    // Find the most recent entry for this URL
    const entry = entries.filter((e) => e.name === url).pop();

    return entry;
};

/**
 * Intercept fetch requests
 */
const interceptFetch = (): void => {
    const originalFetch = window.fetch;

    if (!originalFetch) {
        return;
    }

    window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        const requestId = generateRequestId();
        const startTime = performance.now();

        // Extract URL and method
        let url: string;
        let method: string;

        if (input instanceof Request) {
            url = input.url;
            method = input.method;
        } else {
            url = input.toString();
            method = init?.method ?? 'GET';
        }

        try {
            const response = await originalFetch.call(window, input, init);
            const endTime = performance.now();

            // Mark as JS-initiated before logging to prevent PerformanceObserver duplicate
            markAsJsInitiated(url);

            const message = createMessage<NetworkRequestMessage>({
                type: 'networkRequest',
                requestId,
                method: method.toUpperCase(),
                url,
                statusCode: response.status,
                durationMs: Math.round(endTime - startTime),
                success: response.ok,
                requestType: 'fetch',
                timing: getResourceTiming(url),
            });

            log(message);
            return response;
        } catch (error) {
            const endTime = performance.now();

            // Mark as JS-initiated before logging to prevent PerformanceObserver duplicate
            markAsJsInitiated(url);

            const message = createMessage<NetworkRequestMessage>({
                type: 'networkRequest',
                requestId,
                method: method.toUpperCase(),
                url,
                statusCode: 0,
                durationMs: Math.round(endTime - startTime),
                success: false,
                error: error instanceof Error ? error.message : String(error),
                requestType: 'fetch',
                timing: getResourceTiming(url),
            });

            log(message);
            throw error;
        }
    };
}

/**
 * Intercept XMLHttpRequest
 */
const interceptXHR = (): void => {
    const originalOpen = XMLHttpRequest.prototype.open;
    const originalSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function (
        method: string,
        url: string | URL,
        async: boolean = true,
        username?: string | null,
        password?: string | null,
    ): void {
        // Store request info on the XHR instance
        (
            this as XMLHttpRequest & {
                _bitdrift?: { method: string; url: string; requestId: string };
            }
        )._bitdrift = {
            method: method.toUpperCase(),
            url: url.toString(),
            requestId: generateRequestId(),
        };

        originalOpen.call(this, method, url, async, username, password);
    };

    XMLHttpRequest.prototype.send = function (body?: Document | XMLHttpRequestBodyInit | null): void {
        const xhr = this as XMLHttpRequest & {
            _bitdrift?: { method: string; url: string; requestId: string };
        };
        const info = xhr._bitdrift;

        if (!info) {
            originalSend.call(this, body);
            return;
        }

        const startTime = performance.now();

        const handleComplete = (): void => {
            const endTime = performance.now();

            // Mark as JS-initiated before logging to prevent PerformanceObserver duplicate
            markAsJsInitiated(info.url);

            const message = createMessage<NetworkRequestMessage>({
                type: 'networkRequest',
                requestId: info.requestId,
                method: info.method,
                url: info.url,
                statusCode: xhr.status,
                durationMs: Math.round(endTime - startTime),
                success: xhr.status >= 200 && xhr.status < 400,
                requestType: 'xmlhttprequest',
                timing: getResourceTiming(info.url),
            });

            log(message);
        };

        const handleError = (): void => {
            const endTime = performance.now();

            // Mark as JS-initiated before logging to prevent PerformanceObserver duplicate
            markAsJsInitiated(info.url);

            const message = createMessage<NetworkRequestMessage>({
                type: 'networkRequest',
                requestId: info.requestId,
                method: info.method,
                url: info.url,
                statusCode: 0,
                durationMs: Math.round(endTime - startTime),
                success: false,
                error: 'Network error',
                requestType: 'xmlhttprequest',
            });

            log(message);
        };

        xhr.addEventListener('load', handleComplete);
        xhr.addEventListener('error', handleError);
        xhr.addEventListener('abort', handleError);
        xhr.addEventListener('timeout', handleError);

        originalSend.call(this, body);
    };
}

/**
 * Initialize PerformanceObserver to capture browser-initiated resource loads
 * (images, CSS, scripts, etc.) that weren't made via JS fetch/XHR.
 */
const initResourceObserver = (): void => {
    if (typeof PerformanceObserver === 'undefined') {
        return;
    }

    try {
        const observer = new PerformanceObserver((list) => {
            for (const entry of list.getEntries()) {
                const resourceEntry = entry as PerformanceResourceTiming;

                // Skip if this was already captured via fetch/XHR interception
                if (wasJsInitiated(resourceEntry.name, resourceEntry.responseEnd)) {
                    continue;
                }

                // Skip data URLs and blob URLs (usually internal/generated)
                if (resourceEntry.name.startsWith('data:') || resourceEntry.name.startsWith('blob:')) {
                    continue;
                }

                const durationMs = Math.round(resourceEntry.responseEnd - resourceEntry.startTime);

                // Determine success - if we got timing data and transfer happened, likely successful
                // Note: transferSize can be 0 for cached responses, which is still success
                const hasTimingData = resourceEntry.responseEnd > 0;

                const message = createMessage<NetworkRequestMessage>({
                    type: 'networkRequest',
                    requestId: generateRequestId(),
                    method: 'GET', // Browser resource loads are typically GET
                    url: resourceEntry.name,
                    statusCode: resourceEntry.responseStatus,
                    durationMs,
                    success: hasTimingData,
                    requestType: resourceEntry.initiatorType,
                    timing: resourceEntry,
                });

                log(message);
            }
        });

        observer.observe({ type: 'resource', buffered: false });
    } catch (_error) {
        // TODO (Jackson): Log internal warning: PerformanceObserver not supported or resource type not available
    }
}

/**
 * Initialize network interception for both fetch and XHR,
 * plus PerformanceObserver for browser-initiated resources.
 */
export const initNetworkInterceptor = (): void => {
    interceptFetch();
    interceptXHR();
    initResourceObserver();
};
