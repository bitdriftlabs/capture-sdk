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
import type { InternalAutoInstrumentationMessage, BridgeReadyMessage } from './types';

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
    log(
        createMessage<BridgeReadyMessage>({
            type: 'bridgeReady',
            url: window.location.href,
        }),
    );

    // Initialize page view tracking first to establish parent span
    if (window.bitdrift.config?.capturePageViews) {
        initPageViewTracking();
        log(
            createMessage<InternalAutoInstrumentationMessage>({
                type: 'internalAutoInstrumentation',
                event: 'capturePageViews',
            }),
        );
    }

    // Initialize all monitoring modules
    if (window.bitdrift.config?.captureNetworkRequests) {
        initNetworkInterceptor();
        log(
            createMessage<InternalAutoInstrumentationMessage>({
                type: 'internalAutoInstrumentation',
                event: 'captureNetworkRequests',
            }),
        );
    }
    if (window.bitdrift.config?.captureNavigationEvents) {
        initNavigationTracking();
        log(
            createMessage<InternalAutoInstrumentationMessage>({
                type: 'internalAutoInstrumentation',
                event: 'captureNavigationEvents',
            }),
        );
    }
    if (window.bitdrift.config?.captureWebVitals) {
        initWebVitals();
        log(
            createMessage<InternalAutoInstrumentationMessage>({
                type: 'internalAutoInstrumentation',
                event: 'captureWebVitals',
            }),
        );
    }
    if (window.bitdrift.config?.captureLongTasks) {
        initLongTaskMonitoring();
        log(
            createMessage<InternalAutoInstrumentationMessage>({
                type: 'internalAutoInstrumentation',
                event: 'captureLongTasks',
            }),
        );
    }
    if (window.bitdrift.config?.captureConsole) {
        initConsoleCapture();
        log(
            createMessage<InternalAutoInstrumentationMessage>({
                type: 'internalAutoInstrumentation',
                event: 'captureConsole',
            }),
        );
    }
    if (window.bitdrift.config?.captureUserInteractions) {
        initUserInteractionMonitoring();
        log(
            createMessage<InternalAutoInstrumentationMessage>({
                type: 'internalAutoInstrumentation',
                event: 'captureUserInteractions',
            }),
        );
    }
    if (window.bitdrift.config?.captureErrors) {
        initResourceErrorMonitoring();
        initPromiseRejectionMonitoring();
        initErrorMonitoring();
        log(
            createMessage<InternalAutoInstrumentationMessage>({
                type: 'internalAutoInstrumentation',
                event: 'captureErrors',
            }),
        );
    }
};

// Run immediately
init(/* @magic __BITDRIFT_WEBVIEW_CONFIG_PLACEHOLDER__ */);
