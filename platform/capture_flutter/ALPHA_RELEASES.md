# Flutter Alpha Releases

This record tracks the immutable Git tags customers use to consume the Flutter alpha. It is separate from the root `CHANGELOG.md`, which tracks standard Capture SDK releases. Once Flutter reaches a stable release, this file should become the package-local Flutter changelog.

## [Next release]

Before releasing, update `pubspec.yaml` with the new Flutter alpha version, move these notes to a matching versioned section, add its tag link, and restore this template.

TODO: Before publishing, update the native SDK dependencies in `android/build.gradle.kts` and `ios/capture_flutter.podspec` to the latest compatible published Capture SDK releases.

### Both

**Added**

- Add customer-facing changes here.

**Changed**

- Nothing yet!

**Fixed**

- Nothing yet!

## [0.0.1]
[0.0.1]: https://github.com/bitdriftlabs/capture-sdk/tree/flutter-prototype-0.0.1

### Both

**Added**

- Initial Flutter alpha prototype release.
- Logging, session management, persistent fields, spans, screen views, SDK status, and temporary device codes on Android and iOS.
- Flutter wireframe session replay on Android only.

**Changed**

- Uses the `BitdriftCapture` iOS SDK `~> 0.22` dependency constraint.

**Fixed**

- Nothing yet!
