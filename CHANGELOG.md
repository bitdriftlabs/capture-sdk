# Change Log

## [Unreleased]
[Unreleased]: https://github.com/bitdriftlabs/capture-sdk/compare/v0.19.0...HEAD

### Both

**Added**

- Add API to clear all feature flags.

**Changed**

- Nothing yet!

**Fixed**

- Add back the ability to disable the Session Replay feature at SDK initialization.

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

## [0.19.0] - 2025-10-27
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

- Improve detection for "Service ANR".
- Fix inaccurate frame symbolication in some NDK crash stacktraces.

### iOS

**Fixed**

- Improve performance of Uptime clock computations which should improve the performance all across the SDK.
