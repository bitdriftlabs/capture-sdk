# @bitdrift/react-native-webview

Manual, per-instance WebView instrumentation for the bitdrift React Native SDK.

Install this package only when automatic instrumentation is not the right fit. It supports `react-native-webview` 13.0.0 and newer on iOS, and 13.6.2 and newer on Android (where RNW exposes the native wrapper needed by this package).

```sh
npm install @bitdrift/react-native @bitdrift/react-native-webview react-native-webview
```

Run `pod install` after adding it to an iOS app.

Initialize the base SDK before the `WebView` component mounts. Do not enable automatic WebView instrumentation when using this package for selective instrumentation. On Android, this also means omitting `UNSTABLE_webViewInstrumentation: true` from the Expo config plugin (or `webViewAutomaticInstrumentationScope = JS_ENABLED` from the Gradle plugin):

```tsx
import { init, SessionStrategy } from '@bitdrift/react-native';
import { WebView } from '@bitdrift/react-native-webview';

init('<api key>', SessionStrategy.Activity, {
  UNSTABLE_enableWebViewInstrumentation: false,
});

export function Checkout() {
  return <WebView source={{ uri: 'https://example.com/checkout' }} />;
}
```

The wrapper subclasses the native `react-native-webview` component so it can instrument the native WebView before its first real navigation. It supports iOS and Android and intentionally depends on `react-native-webview` implementation details; validate against the supported RNW version range before upgrading it.
