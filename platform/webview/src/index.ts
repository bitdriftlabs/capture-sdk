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

// Extend Window interface for our guard flag
declare global {
    interface Window {
        __bitdriftBridgeInitialized?: boolean;
    }
}

/**
 * Main entry point for the Bitdrift WebView SDK.
 * This runs immediately when injected into a WebView.
 */
function init(): void {
    // Guard against multiple initializations (e.g., script injected twice)
    if (window.__bitdriftBridgeInitialized) {
        return;
    }
    window.__bitdriftBridgeInitialized = true;

    // Initialize the bridge first
    initBridge();

    // Send bridge ready signal immediately
    const readyMessage = createMessage<BridgeReadyMessage>({
        type: 'bridgeReady',
        url: window.location.href,
    });
    log(readyMessage);

    // Initialize page view tracking first to establish parent span
    initPageViewTracking();

    // Initialize all monitoring modules
    initNetworkInterceptor();
    initNavigationTracking();
    initWebVitals();
    initLongTaskMonitoring();
    initResourceErrorMonitoring();
    initConsoleCapture();
    initPromiseRejectionMonitoring();
    initUserInteractionMonitoring();
    initErrorMonitoring();
}

// Run immediately
init();
