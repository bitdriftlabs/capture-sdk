// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import { safeCall, makeSafe } from './safe-call';
import { wasResourceObserved, markResourceFailed } from './network';

/**
 * Delay before logging a fallback resource error.
 * This gives the PerformanceObserver time to pick up the resource and log it.
 * If PerformanceObserver logs it first, we skip the fallback.
 */
const FALLBACK_DELAY_MS = 500;

/**
 * Initialize resource error monitoring.
 *
 * This module works in coordination with the PerformanceObserver in network.ts:
 * 1. When a DOM error event fires, we record the failure via markResourceFailed()
 * 2. PerformanceObserver checks this when statusCode is unavailable (Safari)
 * 3. After a delay, we log any errors not picked up by PerformanceObserver
 *    (edge cases like CSP-blocked requests that don't create timing entries)
 */
export const initResourceErrorMonitoring = (): void => {
    safeCall(() => {
        // Use capture phase to catch errors before they bubble
        window.addEventListener(
            'error',
            makeSafe((event: ErrorEvent | Event) => {
                // Only handle resource errors, not script errors
                // Script errors have a message property, resource errors don't
                if (event instanceof ErrorEvent) {
                    // This is a script error, not a resource error
                    return;
                }

                const target = event.target as HTMLElement | null;
                if (!target) {
                    return;
                }

                // Check if it's a resource element
                const tagName = target.tagName?.toLowerCase();
                if (!tagName) {
                    return;
                }

                // Only track resource loading elements
                const resourceElements = ['img', 'script', 'link', 'video', 'audio', 'source', 'iframe'];
                if (!resourceElements.includes(tagName)) {
                    return;
                }

                // Get the URL of the failed resource
                const url =
                    (target as HTMLImageElement | HTMLScriptElement | HTMLIFrameElement).src ||
                    (target as HTMLLinkElement).href ||
                    '';

                if (!url) {
                    return;
                }

                const resourceType = getResourceType(tagName, target);

                // Record the failure for PerformanceObserver to consume
                markResourceFailed(url, resourceType, tagName);

                // After a delay, log if PerformanceObserver didn't pick it up
                // This handles edge cases like CSP-blocked requests that don't
                // create Resource Timing entries
                setTimeout(
                    makeSafe(() => {
                        // If PerformanceObserver already logged this URL, skip
                        if (wasResourceObserved(url)) {
                            return;
                        }

                        const message = createMessage({
                            type: 'resourceError',
                            resourceType,
                            url,
                            tagName,
                        });
                        log(message);
                    }),
                    FALLBACK_DELAY_MS,
                );
            }),
            true, // Use capture phase
        );
    });
};

/**
 * Determine the resource type based on tag and attributes
 */
const getResourceType = (tagName: string, element: HTMLElement): string => {
    switch (tagName) {
        case 'img':
            return 'image';
        case 'script':
            return 'script';
        case 'link': {
            const rel = (element as HTMLLinkElement).rel;
            if (rel === 'stylesheet') return 'stylesheet';
            if (rel === 'icon' || rel === 'shortcut icon') return 'icon';
            return 'link';
        }
        case 'video':
            return 'video';
        case 'audio':
            return 'audio';
        case 'source':
            return 'media-source';
        case 'iframe':
            return 'iframe';
        default:
            return tagName;
    }
};
