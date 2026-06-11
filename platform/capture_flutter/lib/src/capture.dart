import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import 'log_level.dart';
import 'session_replay.dart';
import 'span.dart';

/// Session strategy for the Capture SDK.
enum SessionStrategy {
  /// Fixed sessions that persist until explicitly rotated.
  fixed,

  /// Activity-based sessions that rotate on inactivity timeout.
  activityBased,
}

/// Main entry point for the Bitdrift Capture SDK Flutter plugin.
///
/// Call [start] to initialize the SDK, then use static methods for
/// logging, session management, and replay.
class Capture {
  static const _channel = MethodChannel('io.bitdrift.capture_flutter');

  static bool _started = false;
  static bool _replayActive = false;
  static bool _replayCallbackRegistered = false;
  static DateTime _lastReplayCapture = DateTime(0);
  static const _replayInterval = Duration(milliseconds: 500);

  Capture._();

  /// Initialize the Capture SDK.
  ///
  /// Must be called before any other methods. Returns true if started
  /// successfully.
  static Future<bool> start({
    required String apiKey,
    SessionStrategy sessionStrategy = SessionStrategy.fixed,
    String apiUrl = 'https://api.bitdrift.io',
    bool enableSessionReplay = false,
  }) async {
    final result = await _channel.invokeMethod<bool>('start', {
      'apiKey': apiKey,
      'sessionStrategy': sessionStrategy.name,
      'apiUrl': apiUrl,
      'enableSessionReplay': enableSessionReplay,
    });
    _started = result ?? false;
    if (_started &&
        enableSessionReplay &&
        defaultTargetPlatform != TargetPlatform.iOS) {
      startSessionReplay();
    } else {
      stopSessionReplay();
    }
    return _started;
  }

  /// Whether the SDK has been started successfully.
  static bool get isStarted => _started;

  // -- Logging --

  /// Log a message at the given [level] with optional [fields].
  static Future<void> log(
    LogLevel level,
    String message, {
    Map<String, String>? fields,
  }) async {
    await _channel.invokeMethod('log', {
      'level': level.name,
      'message': message,
      if (fields != null) 'fields': fields,
    });
  }

  static Future<void> logTrace(String msg, {Map<String, String>? fields}) =>
      log(LogLevel.trace, msg, fields: fields);

  static Future<void> logDebug(String msg, {Map<String, String>? fields}) =>
      log(LogLevel.debug, msg, fields: fields);

  static Future<void> logInfo(String msg, {Map<String, String>? fields}) =>
      log(LogLevel.info, msg, fields: fields);

  static Future<void> logWarning(String msg, {Map<String, String>? fields}) =>
      log(LogLevel.warning, msg, fields: fields);

  static Future<void> logError(String msg, {Map<String, String>? fields}) =>
      log(LogLevel.error, msg, fields: fields);

  /// Log a screen view event.
  static Future<void> logScreenView(String screenName) =>
      _channel.invokeMethod('logScreenView', {'screenName': screenName});

  // -- Session --

  /// Current session ID, or null if not started.
  static Future<String?> get sessionId =>
      _channel.invokeMethod<String>('getSessionId');

  /// Current session URL, or null if not started.
  static Future<String?> get sessionUrl =>
      _channel.invokeMethod<String>('getSessionUrl');

  /// Device ID, or null if not started.
  static Future<String?> get deviceId =>
      _channel.invokeMethod<String>('getDeviceId');

  /// Create a temporary device code for streaming logs from this device.
  static Future<String?> createTemporaryDeviceCode() =>
      _channel.invokeMethod<String>('createTemporaryDeviceCode');

  /// Start a new session.
  static Future<void> startNewSession() =>
      _channel.invokeMethod('startNewSession');

  /// Get SDK status (initialization state, last handshake, last config delivery).
  static Future<Map<String, dynamic>?> getSdkStatus() async {
    final result = await _channel.invokeMethod<Map>('getSdkStatus');
    return result?.cast<String, dynamic>();
  }

  // -- Fields --

  /// Add a persistent field that will be attached to all future logs.
  static Future<void> addField(String key, String value) =>
      _channel.invokeMethod('addField', {'key': key, 'value': value});

  /// Remove a previously added persistent field.
  static Future<void> removeField(String key) =>
      _channel.invokeMethod('removeField', {'key': key});

  // -- Spans --

  /// Start a new span for tracing.
  static Future<Span?> startSpan(
    String name, {
    LogLevel level = LogLevel.info,
    Map<String, String>? fields,
  }) async {
    final id = await _channel.invokeMethod<String>('startSpan', {
      'name': name,
      'level': level.name,
      if (fields != null) 'fields': fields,
    });
    if (id == null) return null;
    return Span(id: id, name: name);
  }

  /// End an active span.
  static Future<void> endSpan(Span span, {bool success = true}) =>
      _channel.invokeMethod('endSpan', {'spanId': span.id, 'success': success});

  // -- Session Replay --

  /// Send a session replay screen capture (wireframe binary data).
  static Future<void> logReplayScreen(
    Uint8List encodedScreen, {
    double durationSeconds = 0.0,
  }) =>
      _channel.invokeMethod('logReplayScreen', {
        'screen': encodedScreen,
        'duration': durationSeconds,
      });

  /// Start automatic session replay capture.
  ///
  /// This registers a persistent frame callback that captures the widget
  /// tree as wireframe rects at ~2Hz and sends them to the Capture backend.
  static void startSessionReplay() {
    // // TODO: Wire iOS once the native session replay screen API is public/bridged.
    // if (defaultTargetPlatform == TargetPlatform.iOS) return;
    if (_replayActive) return;
    _replayActive = true;
    if (_replayCallbackRegistered) return;
    _replayCallbackRegistered = true;
    WidgetsBinding.instance.addPersistentFrameCallback((_) {
      if (!_replayActive) return;
      final now = DateTime.now();
      if (now.difference(_lastReplayCapture) < _replayInterval) return;
      _lastReplayCapture = now;

      final stopwatch = Stopwatch()..start();
      final encoded = FlutterReplayCapture.captureScreen();
      stopwatch.stop();
      logReplayScreen(
        encoded,
        durationSeconds: stopwatch.elapsedMicroseconds / 1000000.0,
      );
    });
  }

  /// Stop automatic session replay capture.
  static void stopSessionReplay() {
    _replayActive = false;
  }
}
