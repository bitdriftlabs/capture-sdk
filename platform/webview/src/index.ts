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
import type { BridgeReadyMessage } from './types';

/**
 * Main entry point for the Bitdrift WebView SDK.
 * This runs immediately when injected into a WebView.
 */
function init(): void {
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
}

// Run immediately
init();
