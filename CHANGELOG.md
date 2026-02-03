# Change Log

## [Unreleased]
[Unreleased]: https://github.com/bitdriftlabs/capture-sdk/compare/v0.22.4...HEAD

### Both

**Added**

- Nothing yet!

**Changed**

- Nothing yet!

**Fixed**

- Nothing yet!
 
### Android

**Added**

- Nothing yet!

**Changed**

- Nothing yet!

**Fixed**

- Nothing yet!

### iOS

**Added**

- Nothing yet!

**Changed**

- Nothing yet!

**Fixed**

- Nothing yet!

## [0.22.4]

[0.22.4]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.22.4

### Both

**Fixed**

- Fixes an issue causing certain workflows to not work accross process restart.
 
### Android

**Added**

- Expose `baseDomain` Gradle plugin configuration for `bd` CLI uploads.

**Changed**

- Added support for honoring `data-redacted` attributes on elements included in WebView user interaction events.

**Fixed**

- Fixed session replay skipping views with stale text layouts
- Fixed `Untagged socket detected` Strict Mode Violation

## [0.22.3]

[0.22.3]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.22.3

### Android

**Fixed**

- Exclude `androidx.webkit:webkit` dependency from the generated pom.

## [0.22.2]

[0.22.2]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.22.2

### Android

**Added**

- Provide experimental support for WebView instrumentation.

**Changed**

- Update `./gradlew bdUpload` from `io.bitdrift.capture-plugin` to handle automatically upload of sourcemap files.

## [0.22.1]

[0.22.1]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.22.1

### iOS

**Fixed**

- Fixed `Span Success` for network request/responses.

## [0.22.0]

[0.22.0]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.22.0

### Android

**Changed**

- Optimized internal field handling to reduce JVM object allocations.

## [0.21.2]
[0.21.2]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.21.2

### Both

**Added**

- Added support for workflow matching on feature flag state and transitions.
- Fixed an issue where multiple Logger instances could come up and conflict. Subsequent loggers will now noop.

**Changed**

- **BREAKING**: The `variant` parameter in `setFeatureFlagExposure` (Android) / `addFeatureFlagExposure` (iOS) is now required instead of optional.
- Added method overloads to accept `Boolean` / `Bool` values for the `variant` parameter in `setFeatureFlagExposure` (Android) / `addFeatureFlagExposure` (iOS), in addition to the existing `String` parameter.

**Fixed**

- Fixed an issue where multiple Logger instances could come up and conflict. Subsequent loggers will now noop.

### iOS

**Fixed**

- Fixed an issue with JavaScript error reporting in the React Native SDK that prevented stack trace de-minification.

## [0.21.1]
[0.21.1]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.21.1

### Android

**Fixed**

- Made `HttpRequestInfo` and `HttpResponseInfo` properties public so they can be used with the `Logger.log()` methods directly.
- Fix NPE crash when using automatic OkHttp instrumentation via capture-plugin 

## [0.21.0]
[0.21.0]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.21.0

### Both

**Changed**

- The experimental feature flag APIs have been redone and now only expose a single `setFeatureFlagExposure` function instead of generic setters.
 
### Android

**Added**

- Add JVM memory usage percent as a reported OOTB field in Resource Utilization logs.

**Fixed**

- Improve accuracy of low memory detection in `MemoryMetricsProvider`.
- Fix `NoSuchMethodError` runtime exception when consumers target newer versions of `androidx.compose.ui:ui`.

### iOS

**Added**

- Add app memory usage percent as a reported OOTB field in Resource Utilization logs.
- Add new default field for reporting low memory state in Resource Utilization logs.

**Fixed**

- Remove a misleading error log message at launch where no error occurred.

## [0.20.1]
[0.20.1]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.20.1

### Both

**Fixed**

- Fixed an issue where the session ID on AppExit logs would not match the previous process run session ID as expected.
 
### Android

**Changed**

- Removed `enableNativeCrashReporting` flag from `Configuration` object. From now on Native (NDK) crash reports will be included if `enableFatalIssueReporting` is set to true. Please note Native (NDK) crash detection still requires Android 12+ (API level 31 or higher)

**Fixed**

- Fixed a jni LocalReference leak that could crash the app when very large field maps or feature flags were sent to the logger.
- Exclude `com.squareup.retrofit2:retrofit` dependency from the generated pom.

## [0.20.0]
[0.20.0]: https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.20.0

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
