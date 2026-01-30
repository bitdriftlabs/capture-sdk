# Bitdrift WebView JavaScript SDK

This module contains the TypeScript source for the JavaScript bridge that gets injected into WebViews to enable automatic instrumentation and observability for web content rendered within native mobile applications.

For detailed integration instructions and configuration options, see the [WebView Integration documentation](https://docs.bitdrift.io/sdk/integrations.html#webview-android).

## Architecture

The WebView SDK uses a JavaScript bridge to communicate between web content and the native Bitdrift SDK:

```mermaid
graph TB
    subgraph "WebView (JavaScript Context)"
        JS[Web Content]
        Bridge[Bitdrift JS Bridge<br/>(function(){ /* bitdrift script */})()]
        JS -->|Events/Metrics| Bridge
    end
    
    subgraph "Native SDK Layer"
        Handler[Bridge Message Handler]
        Logger[Bitdrift Logger]
        Handler -->|Processed Events| Logger
    end
    
    Bridge -->|JSON Messages| Handler
    
    style Bridge fill:#e1f5ff
    style Handler fill:#fff4e1
    style Logger fill:#f0ffe1
```

### Key Architectural Points

1. **Version-Locked JavaScript Bundle**: The JavaScript bridge (`bitdrift-webview.js`) is embedded directly in the SDK bundle at build time. This ensures the bridge version is always compatible with the native SDK version.

2. **Inline Script Injection**: The JavaScript bridge is injected as an inline script with no remote dependencies. This eliminates the possibility of remote script changes negatively affecting runtime behavior or introducing security vulnerabilities.

3. **Platform-Specific Bridge**: 
   - **Android**: Uses `@JavascriptInterface` exposed as `window.BitdriftLogger`

4. **Automatic Instrumentation**: The bridge automatically captures web events (network requests, navigation, errors, performance metrics) and forwards them to the native SDK without requiring manual integration in the web content.

5. **Isolated Execution**: The bridge runs in a safe, isolated context with error handling to prevent interfering with the web application's functionality.

## Building

```bash
# Install dependencies
npm install

# Build the JavaScript bundle
npm run build

# Generate native SDK files (Kotlin)
npm run generate
```

The build process:
1. Compiles TypeScript sources into a minified JavaScript bundle (`dist/bitdrift-webview.js`)
2. Embeds the bundle as a string constant in the Android SDK source file:
   - `platform/jvm/capture/src/main/kotlin/io/bitdrift/capture/webview/WebViewBridgeScript.kt`

## Integration

The generated native files are automatically included in the SDK build. The JavaScript is injected into WebViews at runtime by the native SDK when WebView capture is enabled.

For usage instructions and configuration options, refer to the [WebView Integration documentation](https://docs.bitdrift.io/sdk/integrations.html#webview-android).
