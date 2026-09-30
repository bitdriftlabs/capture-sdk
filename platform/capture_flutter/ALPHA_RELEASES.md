# Flutter Alpha Releases

This record tracks the immutable Git tags customers use to consume the Flutter alpha. It is separate from the root `CHANGELOG.md`, which tracks standard Capture SDK releases. Once Flutter reaches a stable release, this file should become the package-local Flutter changelog.

## [Next release]

Before releasing, update `pubspec.yaml` with the new Flutter alpha version, move these notes to a matching versioned section, add its tag link, and restore this template.

TODO: Before publishing, update the native SDK dependencies in `android/build.gradle.kts` and `ios/capture_flutter.podspec` to the latest compatible published Capture SDK releases.

## [Unreleased notes here]
### Both

**Added**

- Add customer-facing changes here.

**Changed**

- Add customer-facing changes here.

**Fixed**

- Add customer-facing changes here.

## [0.0.4]
[0.0.4]: https://github.com/bitdriftlabs/capture-sdk/tree/flutter-prototype-0.0.4

### Both

**Added**

- Nothing yet!

**Changed**

- **Breaking:** minimum Flutter bumped to 3.44+ and Dart to 3.12+ (was 3.10+), required for Android's migration to Flutter's Built-in Kotlin support.
- Updated the Android and iOS Capture SDK dependencies to 0.24.1.

**Fixed**

- Android: plugin no longer applies `org.jetbrains.kotlin.android` directly, which was triggering Flutter's Kotlin Gradle Plugin (KGP) deprecation warning and could cause a JVM-target mismatch build failure in consuming apps. Migrated to Flutter's Built-in Kotlin support instead.
- iOS: fixed `ios/capture_flutter/Package.swift` by keeping the `swift-tools-version` comment on the first line, as required by Swift Package Manager.

## [0.0.3]
[0.0.3]: https://github.com/bitdriftlabs/capture-sdk/tree/flutter-prototype-0.0.3

### Both

**Added**

- Nothing yet!

**Changed**

- Nothing yet!

**Fixed**

- Fixed Android builds for apps using `capture_flutter` by explicitly applying the Kotlin Android Gradle plugin.

## [0.0.2]
[0.0.2]: https://github.com/bitdriftlabs/capture-sdk/tree/flutter-prototype-0.0.2

### Both

**Added**

- Added `Capture.setEntityId` and `Capture.clearEntityId`.

**Changed**

- Updated the Android and iOS Capture SDK dependencies to 0.23.12.

## [0.0.1]
[0.0.1]: https://github.com/bitdriftlabs/capture-sdk/tree/flutter-prototype-0.0.1

### Both

**Added**

- Initial Flutter alpha prototype release.
- Logging, session management, persistent fields, spans, screen views, SDK status, and temporary device codes on Android and iOS.
- Flutter wireframe session replay on Android only.

**Changed**

- Uses Android Capture SDK 0.23.0 and the `BitdriftCapture` iOS SDK `~> 0.22` dependency constraint.

**Fixed**

- Nothing yet!
