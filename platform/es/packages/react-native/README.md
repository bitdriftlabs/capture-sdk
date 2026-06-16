# @bitdrift/react-native

bitdrift integration for React Native

## Installation

```sh
npm install @bitdrift/react-native
```

## Usage

### Expo

If you are using Expo to build your React Native app and don't want to use an ejected workflow, you can use the `@bitdrift/react-native` package to initialize the
Capture library and log messages at different log levels. Note that this initializes the library later than is ideal, but should still provide most of the benefits of using Capture.


```js
import {
  init,
  trace,
  debug,
  info,
  warn,
  error,
  setEntityId,
  SessionStrategy,
} from '@bitdrift/react-native';

init('<api key>', SessionStrategy.Activity, {
  startResult: (result) => {
    console.log('Capture start result', result);
  },
  crashReporting: {
    enableNativeFatalIssues: true, // Enable native crash reporting (crashes, ANRs, etc.)
    UNSTABLE_enableJsErrors: true, // Enable JavaScript error reporting (fatal and non-fatal)
    UNSTABLE_onBeforeReportSend: (report) => {
      // Add your custom handling for report metadata.
      // Available fields: report.reportType, report.sessionId, report.details, report.reason, report.fields
    },
  },
});

info('Hello, World!');
setEntityId('user-123');
```

For all Expo usages, make sure to add `@bitdrift/react-native` to the `plugins` field in your `app.json` file. This helps ensure setting up the native modules correctly.

```json
{
  "expo": {
    "plugins": [
      "@bitdrift/react-native"
    ]
  }
}
```

Android-only WebView instrumentation note:

- `UNSTABLE_webViewInstrumentation` currently only affects Android app builds.
- In this package, automatic setup is only provided through the Expo config plugin / generated Android Gradle setup.
- Non-Expo Android apps can still enable the same instrumentation manually by applying the Gradle plugin and configuring the `bitdrift` instrumentation block.
- It does not affect iOS builds.
- It does not automatically configure non-Expo / vanilla React Native Android projects.

#### Expo Go

Due to loading native modules, the `@bitdrift/react-native` package is not supported in Expo Go.

### React Native / Ejected Expo

First initialize the Capture library by using the per-platform instructions found [here](https://docs.bitdrift.io/sdk/quickstart#configuration).

For iOS, perform the initialization in `didFinishLaunchingWithOptions` in your `AppDelegate.m` file.

```objective-c
-
(BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions
{

  [CAPLogger startWithAPIKey:@"<api key>" sessionStrategy: CAPSessionStrategy.fixed]];

  ...
```

For Android, perform the initialization in `onCreate` in your `MainApplication.kt` file.

```kotlin
  override fun onCreate() {
    super.onCreate()
    Capture.Logger.start(
      apiKey = "<api key>",
      sessionStrategy = SessionStrategy.Fixed()
    )
    ...
  }
```

To add custom log messages from your React Native app, import the log level functions from the `@bitdrift/react-native` package and use them to log messages at the desired log level.

## Configuration Options

### Crash Reporting

The `crashReporting` option allows you to configure crash and error reporting behavior:

```js
init('<api key>', SessionStrategy.Activity, {
  crashReporting: {
    enableNativeFatalIssues: true, // Enable native crash reporting (crashes, ANRs, etc.)
    UNSTABLE_enableJsErrors: true, // Enable JavaScript error reporting (fatal and non-fatal)
    UNSTABLE_onBeforeReportSend: (report) => {
      // Add your custom handling for report metadata.
      // Available fields: report.reportType, report.sessionId, report.details, report.reason, report.fields
    },
  }
});
```

- `enableNativeFatalIssues`: When `true`, enables reporting of native fatal issues including crashes, ANRs (Application Not Responding), and other critical errors. Defaults to `true`.
- `UNSTABLE_enableJsErrors`: When `true`, enables reporting of JavaScript errors (both fatal and non-fatal) via React Native's global error handler. Captures unhandled exceptions with stack traces. This feature is experimental and may change in future releases. Defaults to `false`.
- `UNSTABLE_onBeforeReportSend`: Called before an issue report is sent. Receives `{ reportType, reason, details, sessionId, fields }`.

### Previous Run Info

Use `getPreviousRunInfo()` to check whether the previous app run ended in a fatal termination.

```ts
import {
  getPreviousRunInfo,
  init,
  SessionStrategy,
  type PreviousRunInfo,
} from '@bitdrift/react-native';

init('<api key>', SessionStrategy.Activity);

const previousRunInfo: PreviousRunInfo = getPreviousRunInfo();

if (previousRunInfo?.hasFatallyTerminated) {
  console.log('Previous run terminated fatally', previousRunInfo.terminationReason);
}
```

`PreviousRunInfo` shape:

```ts
type PreviousRunInfo = {
  hasFatallyTerminated: boolean;
  terminationReason?: string;
} | null;
```

- This API is synchronous.
- Call it after `init(...)`.
- Android: available on API 30+ (Android 11+); returns `null` on older versions.
- iOS simulator: returns `null` by design; use a physical device to validate.
- `terminationReason` is currently populated on Android. iOS currently provides `hasFatallyTerminated`.

### SDK Status

Use `getSdkStatus()` to inspect the SDK's current initialization state and recent backend activity.

```ts
import { getSdkStatus, init, SessionStrategy, type SdkStatus } from '@bitdrift/react-native';

init('<api key>', SessionStrategy.Activity);

const sdkStatus: SdkStatus = getSdkStatus();
console.log(sdkStatus.initializationState);
```

`SdkStatus` shape:

```ts
type SdkStatus = {
  initializationState: 'notStarted' | 'loaded' | 'running' | 'disabled';
  lastHandshakeTimeMs?: number;
  lastConfigDeliveryTimeMs?: number;
};
```

- This API is synchronous.
- It can be called before or after `init(...)`.
- When Capture has not started yet, `initializationState` is `notStarted`.

### Start Result Callback

Use `startResult` to observe whether native SDK startup succeeded.

```ts
import { init, SessionStrategy } from '@bitdrift/react-native';

init('<api key>', SessionStrategy.Activity, {
  startResult: (result) => {
    if (result.success) {
      console.log('Capture started successfully');
    } else {
      console.log('Capture failed to start', result.error);
    }
  },
});
```

`StartResult` shape:

```ts
type StartResult = {
  success: boolean;
  error?: string;
};
```

- The callback is invoked at most once per `init(...)` call.
- This callback is experimental and may change.

```js
import { trace, debug, info, warn, error } from '@bitdrift/react-native';

// Log at the desired log level using the different log level functions.

trace('Hello, World!');

debug('Hello, World!');

info('Hello, World!');

warn('Hello, World!');

error('Hello, World!');

// Optionally pass an Error object to automatically capture error details
try {
  // some code that might throw
} catch (err) {
  error('Failed to process request', err);
  // Or with additional fields
  error('Failed to process request', err, { userId: '123' });
}

```

### Feature Flag Tracking

Track feature flag exposures to understand which variants users are seeing:

```js
import { setFeatureFlagExposure } from '@bitdrift/react-native';

// Track string variant
setFeatureFlagExposure('dark_mode', 'enabled');
setFeatureFlagExposure('new_ui', 'variant_b');

// Track boolean variant
setFeatureFlagExposure('experimental_feature', true);
setFeatureFlagExposure('beta_mode', false);
```

### Entity Identification

Sets an entity identifier for backend correlation with device identifier. The value is hashed for storage and the exact value is never persisted.

```js
import { setEntityId } from '@bitdrift/react-native';

setEntityId('user-123');
setEntityId('account-456');
```

### Network Integration

The automatic capture of network requests can be achieved in a few different ways depending on the platform and the networking library being used.

#### iOS

When initializing via JS, pass `enableNetworkInstrumentation: true` as one of the options to `init`:

```javascript
import { init, SessionStrategy } from '@bitdrift/react-native';

init('<api key>', sessionStrategy: SessionStrategy.Activity, {
  enableNetworkInstrumentation: true
});
```

Enabling this via the Objective-C API is not yet supported if initialization is done via the native API - please reach out if there is interest in this feature.


#### Android

To enable automatic OkHttp instrumentation on Android, add the `io.bitdrift.capture-plugin` plugin in the app `build.gradle` file:

```gradle
plugins {
    id 'io.bitdrift.capture-plugin' version '<version>'
}
```

To find the version to use, use the same version of the Capture SDK that is being used in the React Native project. Check the `build.gradle` file in the `node_modules/@bitdrift/react-native/android` directory for the version of the Capture SDK being used.

For manual Gradle setup, enable plugin instrumentation:

```gradle
bitdrift {
    instrumentation {
        automaticOkHttpInstrumentation = true
        // Optional. Defaults to PROXY when omitted.
        // okHttpInstrumentationType = OVERWRITE
    }
}
```

In addition to this the plugin repository needs to be added to the `pluginManagement` block in the `settings.gradle` file:

```gradle
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            url 'https://dl.bitdrift.io/sdk/android-maven'
            content {
                includeGroupByRegex "io\\.bitdrift.*"
            }
        }
    }
}
```

When using Expo to generate the project, this can be achieved by setting the `networkInstrumentation` option to `true` in the `app.json` file:

```json
{
  "expo": {
    "plugins": [
        [
          '@bitdrift/react-native',
          {
            networkInstrumentation: true,
          },
        ],
    ]
  }
}
```

When using the Expo plugin with `networkInstrumentation: true`, the Android Gradle plugin and `bitdrift { instrumentation { ... } }` block are generated automatically.

### WebView Integration

`UNSTABLE_webViewInstrumentation` is currently Android-only and is intended for Android app builds.

Runtime capture is enabled separately with `UNSTABLE_webView` in `init(...)`:

```ts
import { init, SessionStrategy } from '@bitdrift/react-native';

init('<api key>', SessionStrategy.Activity, {
  UNSTABLE_webView: {
    capturePageViews: true,
    captureNetworkRequests: true,
    captureNavigationEvents: true,
    captureWebVitals: true,
    captureLongTasks: true,
    captureConsoleLogs: true,
    captureUserInteractions: true,
    captureErrors: true,
  },
});
```

For Expo-generated Android apps, enable build-time WebView bytecode instrumentation with:

```json
{
  "expo": {
    "plugins": [
      [
        "@bitdrift/react-native",
        {
          "UNSTABLE_webViewInstrumentation": true
        }
      ]
    ]
  }
}
```

Notes:

- `UNSTABLE_webViewInstrumentation` currently only affects Android app builds.
- It is not used by iOS.
- It is not automatically applied to vanilla React Native Android builds outside the Expo config plugin flow.
- For non-Expo Android apps, apply the Gradle plugin and `bitdrift { instrumentation { automaticWebViewInstrumentation = true } }` manually.

The Android plugin mode can be configured with `okHttpInstrumentationType`:

```json
{
  "expo": {
    "plugins": [
      [
        "@bitdrift/react-native",
        {
          "networkInstrumentation": true,
          "okHttpInstrumentationType": "OVERWRITE"
        }
      ]
    ]
  }
}
```

`okHttpInstrumentationType` is Android-only and has no effect on iOS.

- `PROXY` (default): preserves existing `EventListener.Factory` behavior in OkHttp clients.
- `OVERWRITE`: replaces the listener factory and can avoid duplicate network spans in some app setups.

If you recently upgraded and started seeing duplicate spans on Android, set `okHttpInstrumentationType` to `OVERWRITE`.
