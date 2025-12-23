// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import type { AnyBridgeMessage } from './types';

/**
 * Platform-agnostic bridge for communicating with native code.
 * Detects iOS (WKWebView) vs Android (WebView) and routes messages accordingly.
 */

interface AndroidBridge {
  log(message: string): void;
}

interface IOSBridge {
  postMessage(message: unknown): void;
}

declare global {
  interface Window {
    // Android bridge
    BitdriftLogger?: AndroidBridge;
    // iOS bridge
    webkit?: {
      messageHandlers?: {
        BitdriftLogger?: IOSBridge;
      };
    };
    // Our global namespace
    bitdrift?: {
      log: (message: AnyBridgeMessage) => void;
    };
  }
}

type Platform = 'ios' | 'android' | 'unknown';

function detectPlatform(): Platform {
  if (window.webkit?.messageHandlers?.BitdriftLogger) {
    return 'ios';
  }
  if (window.BitdriftLogger) {
    return 'android';
  }
  return 'unknown';
}

function sendToNative(message: AnyBridgeMessage): void {
  const platform = detectPlatform();
  const serialized = JSON.stringify(message);

  switch (platform) {
    case 'ios':
      window.webkit?.messageHandlers?.BitdriftLogger?.postMessage(message);
      break;
    case 'android':
      window.BitdriftLogger?.log(serialized);
      break;
    case 'unknown':
      // In development/testing, log to console
      if (typeof console !== 'undefined') {
        console.debug('[Bitdrift WebView]', message);
      }
      break;
  }
}

/**
 * Initialize the global bitdrift object
 */
export function initBridge(): void {
  // Avoid re-initialization
  if (window.bitdrift) {
    return;
  }

  window.bitdrift = {
    log: sendToNative,
  };
}

/**
 * Send a message through the bridge
 */
export function log(message: AnyBridgeMessage): void {
  if (window.bitdrift) {
    window.bitdrift.log(message);
  } else {
    sendToNative(message);
  }
}

/**
 * Helper to create a timestamped message
 */
export function createMessage<T extends AnyBridgeMessage>(
  partial: Omit<T, 'v' | 'timestamp'>
): T {
  return {
    v: 1,
    timestamp: Date.now(),
    ...partial,
  } as T;
}
