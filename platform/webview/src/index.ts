// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { safeCall } from './safe-call';
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

/**
 * Main entry point for the Bitdrift WebView SDK.
 * This runs immediately when injected into a WebView.
 */
const init = (config?: Exclude<(typeof window)['bitdrift'], undefined>['config']): void => {
    safeCall(() => {
        // Guard against multiple initializations (e.g., script injected twice)
        if (window.__bitdriftBridgeInitialized) return;
        window.__bitdriftBridgeInitialized = true;

        window.bitdrift = window.bitdrift || {};
        window.bitdrift.config = config;

        // Initialize the bridge first
        initBridge();

        // Send bridge ready signal immediately
        log(
            createMessage({
                type: 'bridgeReady',
                url: window.location.href,
                instrumentationConfig: window.bitdrift.config,
            }),
        );

        // Initialize page view tracking first to establish parent span
        if (window.bitdrift.config?.capturePageViews) {
            safeCall(() => initPageViewTracking());
            log(
                createMessage({
                    type: 'internalAutoInstrumentation',
                    event: 'capturePageViews',
                }),
            );
        }

        // Initialize all monitoring modules
        if (window.bitdrift.config?.captureNetworkRequests) {
            safeCall(() => initNetworkInterceptor());
            log(
                createMessage({
                    type: 'internalAutoInstrumentation',
                    event: 'captureNetworkRequests',
                }),
            );
        }
        if (window.bitdrift.config?.captureNavigationEvents) {
            safeCall(() => initNavigationTracking());
            log(
                createMessage({
                    type: 'internalAutoInstrumentation',
                    event: 'captureNavigationEvents',
                }),
            );
        }
        if (window.bitdrift.config?.captureWebVitals) {
            safeCall(() => initWebVitals());
            log(
                createMessage({
                    type: 'internalAutoInstrumentation',
                    event: 'captureWebVitals',
                }),
            );
        }
        if (window.bitdrift.config?.captureLongTasks) {
            safeCall(() => initLongTaskMonitoring());
            log(
                createMessage({
                    type: 'internalAutoInstrumentation',
                    event: 'captureLongTasks',
                }),
            );
        }
        if (window.bitdrift.config?.captureConsoleLogs) {
            safeCall(() => initConsoleCapture());
            log(
                createMessage({
                    type: 'internalAutoInstrumentation',
                    event: 'captureConsoleLogs',
                }),
            );
        }
        if (window.bitdrift.config?.captureUserInteractions) {
            safeCall(() => initUserInteractionMonitoring());
            log(
                createMessage({
                    type: 'internalAutoInstrumentation',
                    event: 'captureUserInteractions',
                }),
            );
        }
        if (window.bitdrift.config?.captureErrors) {
            safeCall(() => initResourceErrorMonitoring());
            safeCall(() => initPromiseRejectionMonitoring());
            safeCall(() => initErrorMonitoring());
            log(
                createMessage({
                    type: 'internalAutoInstrumentation',
                    event: 'captureErrors',
                }),
            );
        }
    });
};

// Run immediately
init(/* @magic __BITDRIFT_WEBVIEW_CONFIG_PLACEHOLDER__ */);
