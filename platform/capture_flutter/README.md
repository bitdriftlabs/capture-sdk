# capture_flutter

> Alpha prototype. This package is not yet published to pub.dev. Install an immutable Git tag as described below.

Official Flutter plugin for the [Bitdrift Capture SDK](https://bitdrift.io).

Provides logging, session management, entity correlation, distributed tracing, wireframe session replay, and Dart error reporting for Flutter apps on iOS and Android.

## Installation

```yaml
dependencies:
  capture_flutter:
    git:
      url: https://github.com/bitdriftlabs/capture-sdk.git
      ref: flutter-prototype-0.0.4
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

## Entity

Set an entity identifier to correlate events from this device with an application entity.

```dart
Capture.setEntityId('user-123');
Capture.clearEntityId();
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

On iOS, the Flutter wireframe is merged into the native session replay capture, so screens are captured at the cadence configured for the native SDK.

## Network Logging

```dart
final client = CaptureHttpClient();
await client.get(Uri.parse('https://example.com/items'));
```

Requests made through other clients can be logged manually with
`Capture.logNetworkRequest(HttpRequestInfo(...))` and
`Capture.logNetworkResponse(HttpResponseInfo(...))`.

## App Launch TTI

Measure the time until your app is interactive and report it after the SDK has started; calls made before `Capture.start` completes are dropped:

```dart
Future<void> main() async {
  final ttiStopwatch = Stopwatch()..start();
  WidgetsFlutterBinding.ensureInitialized();
  await Capture.start(apiKey: 'YOUR_API_KEY');
  runApp(const MyApp());
  await WidgetsBinding.instance.waitUntilFirstFrameRasterized;
  Capture.logAppLaunchTTI(ttiStopwatch.elapsed);
}
```

## Dart Error Reporting

Uncaught Dart errors (`FlutterError.onError` and `PlatformDispatcher.instance.onError`) are logged in the current session as `DartError` error logs, with `_error`, `_error_details`, `_stacktrace` and, for framework errors, `_error_context` and `_error_library` fields. Previously installed handlers keep running. Reporting is enabled by default and can be disabled with `enableDartErrorReporting: false`.

```dart
try {
  await riskyOperation();
} catch (error, stack) {
  Capture.reportError(error, stack);
}
```

At most 10 errors are logged per app run. Dart errors don't terminate the app; crashes caused by Dart code (for example through FFI) terminate the process and are reported by the native SDKs as native crashes.

This is a first step: Dart errors are regular logs, so they appear in session timelines and can drive workflows, but not in Issues. The next step is to report them as a dedicated log type.

## Support Matrix

| Area | Android | iOS | Notes |
| :-- | :-- | :-- | :-- |
| SDK start | ✅ | ✅ | `apiKey`, `apiUrl`, `sessionStrategy`, `enableSessionReplay`, `enableDartErrorReporting` |
| Logging | ✅ | ✅ | All levels; fields are `Map<String, String>` |
| Screen views | ✅ | ✅ | Manual `logScreenView` |
| Sessions, session URL, device ID | ✅ | ✅ | |
| Temporary device code | ✅ | ✅ | |
| SDK status | ✅ | ✅ | |
| Persistent fields | ✅ | ✅ | |
| Entity ID | ✅ | ✅ | |
| Spans | ✅ | ✅ | Start/end with success or failure |
| Session replay | ✅ | ✅ | Flutter wireframe capture |
| Native fatal issues | ✅ | ✅ | Reported by the underlying native Capture SDKs |
| Dart exceptions | 🟡 | 🟡 | First step: logged as `DartError` error logs. Next: a dedicated log type |
| Errors on logs | ✅ | ✅ | `error` / `stackTrace` on `log` and the level methods (`logTrace` … `logError`) |
| Feature flags | ✅ | ✅ | String and boolean variants |
| App launch TTI | ✅ | ✅ | Measured by the app; first call per launch is recorded |
| Sleep mode | ✅ | ✅ | At start and at runtime |
| Previous run info | ✅ | ✅ | |
| Start configuration | ✅ | ✅ | `initialFields`, `sleepMode`, `enableFatalIssueReporting` |
| Network logging | ✅ | ✅ | `CaptureHttpClient` for `package:http`, or `logNetworkRequest`/`logNetworkResponse` |
| WebView | ❌ | ❌ | |

Not supported yet:

- WebView instrumentation
- Dart errors as a dedicated log type, so they can be surfaced in Issues
- Field providers and custom date providers at start

## Platform Requirements

- iOS 15.0+
- Android minSdk 23
- Flutter 3.44+ / Dart 3.12+ (required for Android's Built-in Kotlin support — see `ALPHA_RELEASES.md`)

## Releases

The `version` in `pubspec.yaml` is the release source of truth. The matching customer-facing Git tag is `flutter-prototype-<version>`.

Prepare a release in a PR: update `pubspec.yaml`, the installation tag above, and move the reviewed notes from `## [Next release]` to a matching versioned entry in `ALPHA_RELEASES.md`; then restore the empty `Next release` template. When that PR merges to `main`, the `Publish Flutter Alpha Release Tag` GitHub Actions workflow validates the prepared release metadata and creates `flutter-prototype-<version>` from the merge commit.

The CocoaPods `capture_flutter` version follows the native iOS SDK release line and is independent of the Flutter prototype tag.

Before publishing the next Flutter alpha release, update the `BitdriftCapture` dependency in `ios/capture_flutter.podspec` to the latest compatible released iOS SDK version.

Do not retag an existing prototype release: Git consumers depend on its commit remaining immutable.

See `ALPHA_RELEASES.md` for the customer-facing history of Flutter alpha tags.
