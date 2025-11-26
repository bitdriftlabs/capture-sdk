# Change Log

## [Unreleased]
[Unreleased]: https://github.com/bitdriftlabs/capture-sdk/compare/v0.20.0...HEAD

### Both

**Added**

- Nothing yet!

**Changed**

- Nothing yet!

**Fixed**

- Fixed an issue where the session ID on AppExit logs would not match the previous process run session ID as expected.
 
### Android

**Added**

- Nothing yet!

**Changed**

- Removed `enableNativeCrashReporting` flag from `Configuration` object. From now on Native (NDK) crash reports will be included if `enableFatalIssueReporting` is set to true. Please note Native (NDK) crash detection still requires Android 12+ (API level 31 or higher)

**Fixed**

- Fixed a jni LocalReference leak that could crash the app when very large field maps or feature flags were sent to the logger.

### iOS

**Added**

- Nothing yet!

**Changed**

- Nothing yet!

**Fixed**

- Nothing yet!

## [0.20.0]
[0.20.0]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.20.0

### Both

**Added**

- Nothing yet!

**Changed**

- Nothing yet!

**Fixed**

- Nothing yet!

### Android

**Added**

- Add `RetrofitUrlPathProvider` helper class to help extracting endpoint's url paths when using Retrofit services.

**Changed**

- Simplify constructor of `CaptureOkHttpEventListenerFactory`. Remove usage of `(call: Call) -> EventListener)` in favor of `EventListener.Factory`.
- Make `HttpField.PATH_TEMPLATE` constant public so consumers can override it using a custom `OkHttpRequestFieldProvider`.

**Fixed**

- Fix the rendering order and position of dialog and modal windows in Session Replay.

### iOS

**Added**

- Add `rootFileURL` parameter to `Configuration` to allow customizing the storage location for SDK internal files.

**Changed**

- Nothing yet!

**Fixed**

- Nothing yet!

## [0.19.1]
[0.19.1]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.19.1

### Both

**Added**

- Add threshold compression for large message/fields.
- Add support for custom fields in network responses.
- Add API to clear all feature flags.

**Changed**

- Rename `SleepMode` enum values: `ACTIVE` to `ENABLED` and `INACTIVE` to `DISABLED`.

**Fixed**

- Improved ring buffer serialization format for more efficient space usage.
- Add back the ability to disable the Session Replay feature at SDK initialization.

### Android

**Fixed**

- Remove internal OkHttp reference.

### iOS

**Changed**

- Make `HTTPRequestMetrics.init` public.

## [0.19.0]
[0.19.0]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.19.0

### Both

**Added**

- Add support for free-tier bitdrift plans.

**Fixed**

- Improve performance of Feature Flags writes.

### Android

**Added**

- Add ANR detection for "App Start ANR".

**Fixed**

- Improve detection for "Service ANR" for when a Service takes too long to start.
- Fix inaccurate frame symbolication in some NDK crash stacktraces.

### iOS

**Fixed**

- Improve performance of Uptime clock computations which should improve the performance all across the SDK.
