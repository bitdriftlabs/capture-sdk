# capture_flutter

> Alpha prototype. This package is not yet published to pub.dev. Install an immutable Git tag as described below.

Official Flutter plugin for the [Bitdrift Capture SDK](https://bitdrift.io).

Provides logging, session management, and distributed tracing for Flutter apps on iOS and Android. Wireframe session replay is currently available on Android only.

## Installation

```yaml
dependencies:
  capture_flutter:
    git:
      url: https://github.com/bitdriftlabs/capture-sdk.git
      ref: flutter-prototype-0.0.1
      path: platform/capture_flutter
```

The Flutter package version is defined in [`pubspec.yaml`](pubspec.yaml). Each published alpha is available from an immutable `flutter-prototype-<version>` Git tag; see [`ALPHA_RELEASES.md`](ALPHA_RELEASES.md) for release history and the latest tag.

## Quick Start

```dart
import 'package:capture_flutter/capture_flutter.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Capture.start(
    apiKey: 'YOUR_API_KEY',
    enableSessionReplay: true,
  );

  runApp(MyApp());
}
```

## Logging

```dart
Capture.logInfo('User signed in', fields: {'user_id': '123'});
Capture.logWarning('Slow network response');
Capture.logError('Payment failed', fields: {'code': '402'});
```

## Sessions

```dart
final sessionId = await Capture.sessionId;
final sessionUrl = await Capture.sessionUrl;

await Capture.startNewSession();
```

## Fields

```dart
Capture.addField('app_version', '2.1.0');
Capture.removeField('app_version');
```

## Spans

```dart
final span = await Capture.startSpan('loadData');
try {
  await fetchData();
  await Capture.endSpan(span!, success: true);
} catch (e) {
  await Capture.endSpan(span!, success: false);
}
```

## Session Replay

Session replay captures a wireframe representation of your Flutter UI (no screenshots, no PII) and sends it to the Capture backend.

```dart
await Capture.start(
  apiKey: 'YOUR_API_KEY',
  enableSessionReplay: true,
);

// Stop when no longer needed
Capture.stopSessionReplay();
```

## Platform Requirements

- iOS 15.0+
- Android minSdk 23
- Flutter 3.10+

## Releases

The `version` in `pubspec.yaml` is the release source of truth. The matching customer-facing Git tag is `flutter-prototype-<version>`; with the current version, use `flutter-prototype-0.0.1`.

Prepare a release in a PR: update `pubspec.yaml`, the installation tag above, and move the reviewed notes from `## [Next release]` to a matching versioned entry in `ALPHA_RELEASES.md`; then restore the empty `Next release` template. When that PR merges to `main`, the `Publish Flutter Alpha Release Tag` GitHub Actions workflow validates the prepared release metadata and creates `flutter-prototype-<version>` from the merge commit.

The CocoaPods `capture_flutter` version follows the native iOS SDK release line and is independent of the Flutter prototype tag.

Before publishing the next Flutter alpha release, update the `BitdriftCapture` dependency in `ios/capture_flutter.podspec` to the latest compatible released iOS SDK version.

Do not retag an existing prototype release: Git consumers depend on its commit remaining immutable.

See `ALPHA_RELEASES.md` for the customer-facing history of Flutter alpha tags.
