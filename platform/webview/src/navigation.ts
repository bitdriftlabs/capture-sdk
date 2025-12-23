// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
<<<<<<< HEAD
import { startPageView } from './page-view';
=======
>>>>>>> a7d8e8b7 (first pass on setting up JS bridge with some performance tracking)
import type { NavigationMessage } from './types';

let currentUrl = '';

/**
 * Initialize SPA navigation tracking via History API
 */
<<<<<<< HEAD
export const initNavigationTracking = (): void => {
    currentUrl = window.location.href;

    // Intercept pushState
    const originalPushState = history.pushState;
    history.pushState = function (data: unknown, unused: string, url?: string | URL | null): void {
        const fromUrl = currentUrl;
        originalPushState.call(this, data, unused, url);
        const toUrl = window.location.href;

        if (fromUrl !== toUrl) {
            currentUrl = toUrl;
            logNavigation(fromUrl, toUrl, 'pushState');
            // Start new page view span for SPA navigation
            startPageView(toUrl, 'navigation');
        }
    };

    // Intercept replaceState
    const originalReplaceState = history.replaceState;
    history.replaceState = function (data: unknown, unused: string, url?: string | URL | null): void {
        const fromUrl = currentUrl;
        originalReplaceState.call(this, data, unused, url);
        const toUrl = window.location.href;

        if (fromUrl !== toUrl) {
            currentUrl = toUrl;
            logNavigation(fromUrl, toUrl, 'replaceState');
            // Start new page view span for SPA navigation
            startPageView(toUrl, 'navigation');
        }
    };

    // Listen for popstate (back/forward navigation)
    window.addEventListener('popstate', () => {
        const fromUrl = currentUrl;
        const toUrl = window.location.href;

        if (fromUrl !== toUrl) {
            currentUrl = toUrl;
            logNavigation(fromUrl, toUrl, 'popstate');
            // Start new page view span for back/forward navigation
            startPageView(toUrl, 'navigation');
        }
    });
};

const logNavigation = (fromUrl: string, toUrl: string, method: 'pushState' | 'replaceState' | 'popstate'): void => {
    const message = createMessage<NavigationMessage>({
        type: 'navigation',
        fromUrl,
        toUrl,
        method,
    });
    log(message);
};
=======
export function initNavigationTracking(): void {
  currentUrl = window.location.href;

  // Intercept pushState
  const originalPushState = history.pushState;
  history.pushState = function(
    data: unknown,
    unused: string,
    url?: string | URL | null
  ): void {
    const fromUrl = currentUrl;
    originalPushState.call(this, data, unused, url);
    const toUrl = window.location.href;
    
    if (fromUrl !== toUrl) {
      currentUrl = toUrl;
      logNavigation(fromUrl, toUrl, 'pushState');
    }
  };

  // Intercept replaceState
  const originalReplaceState = history.replaceState;
  history.replaceState = function(
    data: unknown,
    unused: string,
    url?: string | URL | null
  ): void {
    const fromUrl = currentUrl;
    originalReplaceState.call(this, data, unused, url);
    const toUrl = window.location.href;
    
    if (fromUrl !== toUrl) {
      currentUrl = toUrl;
      logNavigation(fromUrl, toUrl, 'replaceState');
    }
  };

  // Listen for popstate (back/forward navigation)
  window.addEventListener('popstate', () => {
    const fromUrl = currentUrl;
    const toUrl = window.location.href;
    
    if (fromUrl !== toUrl) {
      currentUrl = toUrl;
      logNavigation(fromUrl, toUrl, 'popstate');
    }
  });
}

function logNavigation(
  fromUrl: string, 
  toUrl: string, 
  method: 'pushState' | 'replaceState' | 'popstate'
): void {
  const message = createMessage<NavigationMessage>({
    type: 'navigation',
    fromUrl,
    toUrl,
    method,
  });
  log(message);
}
>>>>>>> a7d8e8b7 (first pass on setting up JS bridge with some performance tracking)
