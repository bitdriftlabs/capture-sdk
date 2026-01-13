// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import type { ResourceErrorMessage } from './types';

/**
 * Initialize resource error monitoring.
 * Captures failed loads for images, scripts, stylesheets, etc.
 */
export const initResourceErrorMonitoring = (): void => {
    // Use capture phase to catch errors before they bubble
    window.addEventListener(
        'error',
        (event: ErrorEvent | Event) => {
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

            const message = createMessage<ResourceErrorMessage>({
                type: 'resourceError',
                resourceType: getResourceType(tagName, target),
                url,
                tagName,
            });
            log(message);
        },
        true, // Use capture phase
    );
}

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
