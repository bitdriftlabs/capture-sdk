# Bitdrift WebView JavaScript SDK

This module contains the TypeScript source for the JavaScript bridge that gets injected into WebViews to capture:

- **Core Web Vitals**: LCP, CLS, INP, FCP, TTFB via the `web-vitals` library
- **Network requests**: Intercepted `fetch` and `XMLHttpRequest` calls with timing data
- **SPA navigation**: History API changes (`pushState`, `replaceState`, `popstate`)
- **JavaScript errors**: Uncaught errors with stack traces

## Building

```bash
# Install dependencies
npm install

# Build the bundle
npm run build

# Generate native files (Kotlin + Swift)
npm run generate
```

## Output

The build process generates:
- `dist/bitdrift-webview.js` - The minified JavaScript bundle

The `npm run generate` command outputs directly to the SDK source directories:
- `platform/jvm/capture/src/main/kotlin/io/bitdrift/capture/webview/WebViewBridgeScript.kt`
- `platform/swift/source/integrations/webview/WebViewBridgeScript.swift`

## Architecture

```
src/
├── index.ts          # Entry point, initializes all modules
├── types.ts          # TypeScript interfaces for bridge messages
├── bridge.ts         # Native bridge abstraction (iOS/Android)
├── web-vitals.ts     # Core Web Vitals monitoring
├── network.ts        # fetch/XHR interception
└── navigation.ts     # History API tracking
```

## Bridge Protocol

All messages use a versioned JSON format:

```typescript
{
  v: 1,                    // Protocol version
  timestamp: 1703123456789, // Unix timestamp in ms
  type: 'webVital' | 'networkRequest' | 'navigation' | 'error' | 'bridgeReady',
  // ... type-specific fields
}
```

### Message Types

| Type | Description |
|------|-------------|
| `bridgeReady` | Sent immediately when the script executes |
| `webVital` | Core Web Vital metric (LCP, CLS, etc.) |
| `networkRequest` | Captured fetch/XHR request with timing |
| `navigation` | SPA navigation via History API |
| `error` | Uncaught JavaScript error |

## Integration

The `npm run generate` command automatically writes the generated files to the correct SDK locations. No manual copy step is required.
