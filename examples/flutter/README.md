# Flutter Capture SDK Example

A sample Flutter app demonstrating integration with the Bitdrift Capture SDK
using platform channels.

## Setup

1. Install dependencies:
   ```bash
   flutter pub get
   ```

2. Run:
   ```bash
   flutter run
   ```

3. Tap the settings icon and enter your Bitdrift API key (and optionally override the API URL).

## Architecture

This example uses Flutter platform channels (`MethodChannel`) to bridge Dart
code to the native Capture SDK on each platform:

- **Dart** (`lib/capture_logger.dart`) - Platform-agnostic API wrapper
- **Android** (`android/.../MainActivity.kt`) - Kotlin bridge using `io.bitdrift:capture`
- **iOS** (`ios/Runner/AppDelegate.swift`) - Swift bridge using the `Capture` pod

## Features Demonstrated

- SDK initialization
- Logging at various levels (trace, debug, info, warning, error)
- Attaching custom fields to log messages
- Session management (get ID, start new session)
