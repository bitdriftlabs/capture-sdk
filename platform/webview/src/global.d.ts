export {};

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

type WebViewInstrumentationConfig = {
    captureConsole?: boolean;
    captureErrors?: boolean;
    capturePromiseRejections?: boolean;
    captureNetworkRequests?: boolean;
    captureNavigationEvents?: boolean;
    capturePageViews?: boolean;
    captureWebVitals?: boolean;
    captureLongTasks?: boolean;
    captureResourceErrors?: boolean;
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
