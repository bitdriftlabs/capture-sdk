# capture_flutter

Official Flutter plugin for the [Bitdrift Capture SDK](https://bitdrift.io).

Provides logging, session management, distributed tracing, and session replay for Flutter apps on iOS and Android.

## Installation

```yaml
dependencies:
  capture_flutter: ^0.22.16
```

## Quick Start

```dart
import 'package:capture_flutter/capture_flutter.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Capture.start(apiKey: 'YOUR_API_KEY');
  Capture.startSessionReplay();

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
// Start after SDK initialization
Capture.startSessionReplay();

// Stop when no longer needed
Capture.stopSessionReplay();
```

## Platform Requirements

- iOS 15.0+
- Android minSdk 23
- Flutter 3.10+
