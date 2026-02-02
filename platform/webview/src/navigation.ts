// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import { safeCall, makeSafe } from './safe-call';
import { startPageView } from './page-view';

let currentUrl = '';

/**
 * Initialize SPA navigation tracking via History API
 */
export const initNavigationTracking = (): void => {
    safeCall(() => {
        currentUrl = window.location.href;

        // Intercept pushState
        const originalPushState = history.pushState;
        history.pushState = function (data: unknown, unused: string, url?: string | URL | null): void {
            originalPushState.call(this, data, unused, url);
            safeCall(() => {
                const fromUrl = currentUrl;
                const toUrl = window.location.href;

                if (fromUrl !== toUrl) {
                    currentUrl = toUrl;
                    logNavigation(fromUrl, toUrl, 'pushState');
                    // Start new page view span for SPA navigation
                    startPageView(toUrl, 'navigation');
                }
            });
        };

        // Intercept replaceState
        const originalReplaceState = history.replaceState;
        history.replaceState = function (data: unknown, unused: string, url?: string | URL | null): void {
            originalReplaceState.call(this, data, unused, url);
            safeCall(() => {
                const fromUrl = currentUrl;
                const toUrl = window.location.href;

                if (fromUrl !== toUrl) {
                    currentUrl = toUrl;
                    logNavigation(fromUrl, toUrl, 'replaceState');
                    // Start new page view span for SPA navigation
                    startPageView(toUrl, 'navigation');
                }
            });
        };

        // Listen for popstate (back/forward navigation)
        window.addEventListener(
            'popstate',
            makeSafe(() => {
                const fromUrl = currentUrl;
                const toUrl = window.location.href;

                if (fromUrl !== toUrl) {
                    currentUrl = toUrl;
                    logNavigation(fromUrl, toUrl, 'popstate');
                    // Start new page view span for back/forward navigation
                    startPageView(toUrl, 'navigation');
                }
            }),
        );
    });
};

const logNavigation = (fromUrl: string, toUrl: string, method: 'pushState' | 'replaceState' | 'popstate'): void => {
    const message = createMessage({
        type: 'navigation',
        fromUrl,
        toUrl,
        method,
        fields: {
            _from_url: fromUrl,
            _to_url: toUrl,
            _method: method,
        },
    });
    log(message);
};
