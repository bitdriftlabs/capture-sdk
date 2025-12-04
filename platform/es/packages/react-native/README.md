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
import { init, trace, debug, info, warn, error, SessionStrategy } from '@bitdrift/react-native';

init('<api key>', SessionStrategy.Activity, {
  crashReporting: {
    enableNativeFatalIssues: true, // Enable native crash reporting (crashes, ANRs, etc.)
    UNSTABLE_enableJsErrors: true, // Enable JavaScript error reporting (fatal and non-fatal)
  },
});

info('Hello, World!');
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
  }
});
```

- `enableNativeFatalIssues`: When `true`, enables reporting of native fatal issues including crashes, ANRs (Application Not Responding), and other critical errors. Defaults to `true`.
- `UNSTABLE_enableJsErrors`: When `true`, enables reporting of JavaScript errors (both fatal and non-fatal) via React Native's global error handler. Captures unhandled exceptions with stack traces. This feature is experimental and may change in future releases. Defaults to `false`.

```js
import { trace, debug, info, warn, error } from '@bitdrift/react-native';

// Log at the desired log level using the different log level functions.

trace('Hello, World!');

debug('Hello, World!');

info('Hello, World!');

warn('Hello, World!');

error('Hello, World!');

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

To enable network integration in Android a Gradle plugin needs to be added to the project. This can be done by adding a dependency on the `io.bitdrift.capture-plugin` plugin in the `plugins` block of the apps's `build.gradle` file:

```gradle
plugins {
    id 'io.bitdrift.capture-plugin' version '<version>'
}
```

To find the version to use, use the same version of the Capture SDK that is being used in the React Native project. Check the `build.gradle` file in the `node_modules/@bitdrift/react-native/android` directory for the version of the Capture SDK being used.

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
