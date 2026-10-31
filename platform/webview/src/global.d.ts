// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

export {};

/**
 * Platform-agnostic bridge for communicating with native code.
 * Detects iOS (WKWebView) vs Android (WebView) and routes messages accordingly.
 */

interface AndroidBridge {
    postMessage(message: string): void;
}

interface IOSBridge {
    postMessage(message: unknown): void;
}

type WebViewInstrumentationConfig = {
    captureConsoleLogs?: boolean;
    captureErrors?: boolean;
    captureNetworkRequests?: boolean;
    captureNavigationEvents?: boolean;
    capturePageViews?: boolean;
    captureWebVitals?: boolean;
    captureLongTasks?: boolean;
    captureUserInteractions?: boolean;
};

declare global {
    interface Window {
        // Extend Window interface for our guard flag
        __bitdriftBridgeInitialized?: boolean;
        // Android bridge
        BitdriftLogger?: AndroidBridge;
        // iOS bridge
        webkit?: {
            messageHandlers?: {
                BitdriftLogger?: IOSBridge;
            };
        };
        // Our global namespace
        bitdrift?: Partial<{
            config: Partial<WebViewInstrumentationConfig>;
            log: (message: AnyBridgeMessage) => void;
        }>;
    }
}
