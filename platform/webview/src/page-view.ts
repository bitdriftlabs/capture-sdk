// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import { safeCall, makeSafe } from './safe-call';

/** Current page view span ID */
let currentPageSpanId: string | null = null;

/** Start time of current page view (epoch ms) */
let pageViewStartTimeMs: number = 0;

/**
 * Generate a unique span ID
 */
const generateSpanId = (): string => {
    // Use crypto.randomUUID if available, otherwise fallback
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        return crypto.randomUUID();
    }
    // Fallback for older environments
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
};

/**
 * Get the current page view span ID.
 * Can be used by other modules to nest their spans/logs under the current page view.
 */
export const getCurrentPageSpanId = (): string | null => {
    return currentPageSpanId;
};

/**
 * Start a new page view span.
 * This will end any existing page view span first.
 *
 * For the initial page view, we use performance.timeOrigin as the start time
 * so that web vitals (which are measured from navigation start) fall within
 * the page view span.
 */
export const startPageView = (url: string, reason: 'initial' | 'navigation' = 'navigation'): void => {
    safeCall(() => {
        // End previous page view if exists
        if (currentPageSpanId) {
            endPageView('navigation');
        }

        currentPageSpanId = generateSpanId();

        // For initial page view, use navigation start time (performance.timeOrigin)
        // For SPA navigations, use current time
        if (reason === 'initial') {
            pageViewStartTimeMs = Math.round(performance.timeOrigin);
        } else {
            pageViewStartTimeMs = Date.now();
        }

        const message = createMessage({
            type: 'pageView',
            action: 'start',
            spanId: currentPageSpanId,
            url,
            reason,
            // Use our calculated start time, not Date.now()
            timestamp: pageViewStartTimeMs,
            fields: {
                _span_id: currentPageSpanId,
                _url: url,
                _reason: reason,
            },
        });
        log(message);
    });
};

/**
 * End the current page view span.
 */
export const endPageView = (reason: 'navigation' | 'unload' | 'hidden'): void => {
    safeCall(() => {
        if (!currentPageSpanId) {
            return;
        }

        const now = Date.now();
        const durationMs = now - pageViewStartTimeMs;

        const message = createMessage({
            type: 'pageView',
            action: 'end',
            spanId: currentPageSpanId,
            url: window.location.href,
            reason,
            durationMs,
            timestamp: now,
            fields: {
                _span_id: currentPageSpanId,
                _url: window.location.href,
                _reason: reason,
                _duration_ms: durationMs.toString(),
            },
        });
        log(message);

        currentPageSpanId = null;
        pageViewStartTimeMs = 0;
    });
};

/**
 * Log a lifecycle event within the current page view.
 */
const logLifecycleEvent = (
    event: 'DOMContentLoaded' | 'load' | 'visibilitychange',
    details?: Record<string, string>,
): void => {
    safeCall(() => {
        const fields: Record<string, string> = {
            _event: event,
            _performance_time: performance.now().toString(),
        };

        if (details?.visibilityState) {
            fields._visibility_state = details.visibilityState;
        }

        const message = createMessage({
            type: 'lifecycle',
            event,
            performanceTime: performance.now(),
            ...details,
            fields,
        });
        log(message);
    });
};

/**
 * Initialize page view tracking.
 * This sets up the initial page view and lifecycle event listeners.
 */
export const initPageViewTracking = (): void => {
    safeCall(() => {
        // Start initial page view
        startPageView(window.location.href, 'initial');

        // Track DOMContentLoaded
        if (document.readyState === 'loading') {
            document.addEventListener(
                'DOMContentLoaded',
                makeSafe(() => {
                    logLifecycleEvent('DOMContentLoaded');
                }),
            );
        } else {
            // Already loaded, log immediately with note
            logLifecycleEvent('DOMContentLoaded');
        }

        // Track window load
        if (document.readyState !== 'complete') {
            window.addEventListener(
                'load',
                makeSafe(() => {
                    logLifecycleEvent('load');
                }),
            );
        } else {
            // Already loaded
            logLifecycleEvent('load');
        }

        // Track visibility changes
        document.addEventListener(
            'visibilitychange',
            makeSafe(() => {
                logLifecycleEvent('visibilitychange', {
                    visibilityState: document.visibilityState,
                });

                // End page view when hidden (user switched tabs/apps)
                // This ensures CLS/INP are captured before the page is hidden
                if (document.visibilityState === 'hidden') {
                    endPageView('hidden');
                } else if (document.visibilityState === 'visible' && !currentPageSpanId) {
                    // Resume page view when becoming visible again
                    startPageView(window.location.href, 'navigation');
                }
            }),
        );

        // Track page unload
        window.addEventListener(
            'pagehide',
            makeSafe(() => {
                endPageView('unload');
            }),
        );

        // Fallback for browsers that don't support pagehide
        window.addEventListener(
            'beforeunload',
            makeSafe(() => {
                endPageView('unload');
            }),
        );
    });
};
