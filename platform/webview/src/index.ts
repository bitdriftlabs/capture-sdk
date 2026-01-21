// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { initBridge, log, createMessage } from './bridge';
import { initWebVitals } from './web-vitals';
import { initNetworkInterceptor } from './network';
import { initNavigationTracking } from './navigation';
import { initPageViewTracking } from './page-view';
import { initLongTaskMonitoring } from './long-tasks';
import { initResourceErrorMonitoring } from './resource-errors';
import { initConsoleCapture } from './console-capture';
import { initUserInteractionMonitoring } from './user-interactions';
import { initErrorMonitoring, initPromiseRejectionMonitoring } from './error';
import type { BridgeReadyMessage } from './types';

/**
 * Main entry point for the Bitdrift WebView SDK.
 * This runs immediately when injected into a WebView.
 */
const init = (config?: Exclude<(typeof window)['bitdrift'], undefined>['config']): void => {
    // Guard against multiple initializations (e.g., script injected twice)
    if (window.__bitdriftBridgeInitialized) return;
    window.__bitdriftBridgeInitialized = true;

    window.bitdrift = window.bitdrift || {};
    window.bitdrift.config = config;

    // Initialize the bridge first
    initBridge();

    // Send bridge ready signal immediately
    const readyMessage = createMessage<BridgeReadyMessage>({
        type: 'bridgeReady',
        url: window.location.href,
    });
    log(readyMessage);

    // Initialize page view tracking first to establish parent span
    if (window.bitdrift.config?.capturePageViews) {
        initPageViewTracking();
    }

    // Initialize all monitoring modules
    if (window.bitdrift.config?.captureNetworkRequests) {
        initNetworkInterceptor();
    }
    if (window.bitdrift.config?.captureNavigationEvents) {
        initNavigationTracking();
    }
    if (window.bitdrift.config?.captureWebVitals) {
        initWebVitals();
    }
    if (window.bitdrift.config?.captureLongTasks) {
        initLongTaskMonitoring();
    }
    if (window.bitdrift.config?.captureConsole) {
        initConsoleCapture();
    }
    if (window.bitdrift.config?.captureUserInteractions) {
        initUserInteractionMonitoring();
    }
    if (window.bitdrift.config?.captureErrors) {
        initResourceErrorMonitoring();
        initPromiseRejectionMonitoring();
        initErrorMonitoring();
    }
};

// Run immediately
init(/* @magic __BITDRIFT_WEBVIEW_CONFIG_PLACEHOLDER__ */);
