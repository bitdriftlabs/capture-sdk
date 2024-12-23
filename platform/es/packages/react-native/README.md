# @bitdrift/react-native

bitdrift integration for React Native

## Installation

```sh
npm install @bitdrift/react-native
```

## Usage

## Expo

If you are using Expo to build your React Native app and don't want to use an ejected workflow, you can use the `@bitdrift/react-native` package to initialize the
Capture library and log messages at different log levels. Note that this initializes the library later than is ideal, but should still provide most of the benefits of using Capture.


```js
import { init, trace, debug, info, warn, error } from '@bitdrift/react-native';
init('<api key>', 'activity'); // Specify either `activity` or `fixed` session strategy

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

## Expo Go

Due to loading native modules, the `@bitdrift/react-native` package is not supported in Expo Go.

## React Native / Ejected Expo

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

```js
import { trace, debug, info, warn, error } from '@bitdrift/react-native';

// Log at the desired log level using the different log level functions.

trace('Hello, World!');

debug('Hello, World!');

info('Hello, World!');

warn('Hello, World!');

error('Hello, World!');

```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

