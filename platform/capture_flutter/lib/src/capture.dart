import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import 'configuration.dart';
import 'error_reporter.dart';
import 'log_level.dart';
import 'network.dart';
import 'replay_scheduler.dart';
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
  static Int32List? _lastPushedReplayRects;
  static final _replayScheduler = ReplayCaptureScheduler(_captureReplay);

  Capture._();

  /// Initialize the Capture SDK.
  ///
  /// Must be called before any other methods. Returns true if started
  /// successfully.
  ///
  /// When [enableDartErrorReporting] is true, uncaught Dart errors are
  /// logged as `DartError` error logs.
  /// [enableFatalIssueReporting] controls native crash and ANR reporting.
  /// [initialFields] are attached to every log from the start of the
  /// session.
  static Future<bool> start({
    required String apiKey,
    SessionStrategy sessionStrategy = SessionStrategy.fixed,
    String apiUrl = 'https://api.bitdrift.io',
    bool enableSessionReplay = false,
    bool enableDartErrorReporting = true,
    bool enableFatalIssueReporting = true,
    SleepMode sleepMode = SleepMode.disabled,
    Map<String, String> initialFields = const {},
  }) async {
    final result = await _channel.invokeMethod<bool>('start', {
      'apiKey': apiKey,
      'sessionStrategy': sessionStrategy.name,
      'apiUrl': apiUrl,
      'enableSessionReplay': enableSessionReplay,
      'enableFatalIssueReporting': enableFatalIssueReporting,
      'sleepMode': sleepMode.name,
      'initialFields': initialFields,
    });
    _started = result ?? false;
    if (_started && enableSessionReplay) {
      startSessionReplay();
    } else {
      stopSessionReplay();
    }
    if (_started && enableDartErrorReporting) {
      DartErrorReporter.installHandlers(
        (fields) => log(LogLevel.error, 'DartError', fields: fields),
      );
    }
    return _started;
  }

  /// Whether the SDK has been started successfully.
  static bool get isStarted => _started;

  // -- Logging --

  /// Log a message at the given [level] with optional [fields].
  ///
  /// When an [error] is provided its type and description are attached as
  /// `_error` and `_error_details`, and [stackTrace] as `_stacktrace`. Without
  /// a [stackTrace], the trace recorded by a Dart [Error] is used.
  static Future<void> log(
    LogLevel level,
    String message, {
    Map<String, String>? fields,
    Object? error,
    StackTrace? stackTrace,
  }) async {
    final allFields = {
      ...?fields,
      if (error != null) ...{
        '_error': error.runtimeType.toString(),
        '_error_details': error.toString(),
      },
      ...DartErrorReporter.stackTraceFields(error, stackTrace),
    };
    await _channel.invokeMethod('log', {
      'level': level.name,
      'message': message,
      if (allFields.isNotEmpty) 'fields': allFields,
    });
  }

  static Future<void> logTrace(
    String msg, {
    Map<String, String>? fields,
    Object? error,
    StackTrace? stackTrace,
  }) => log(
    LogLevel.trace,
    msg,
    fields: fields,
    error: error,
    stackTrace: stackTrace,
  );

  static Future<void> logDebug(
    String msg, {
    Map<String, String>? fields,
    Object? error,
    StackTrace? stackTrace,
  }) => log(
    LogLevel.debug,
    msg,
    fields: fields,
    error: error,
    stackTrace: stackTrace,
  );

  static Future<void> logInfo(
    String msg, {
    Map<String, String>? fields,
    Object? error,
    StackTrace? stackTrace,
  }) => log(
    LogLevel.info,
    msg,
    fields: fields,
    error: error,
    stackTrace: stackTrace,
  );

  static Future<void> logWarning(
    String msg, {
    Map<String, String>? fields,
    Object? error,
    StackTrace? stackTrace,
  }) => log(
    LogLevel.warning,
    msg,
    fields: fields,
    error: error,
    stackTrace: stackTrace,
  );

  static Future<void> logError(
    String msg, {
    Map<String, String>? fields,
    Object? error,
    StackTrace? stackTrace,
  }) => log(
    LogLevel.error,
    msg,
    fields: fields,
    error: error,
    stackTrace: stackTrace,
  );

  /// Log the time it took for the app to become interactive after launch, as
  /// measured by the app (for example, until its first meaningful screen is
  /// rendered). Only the first call per process is recorded, and the SDK must
  /// be started.
  static Future<void> logAppLaunchTTI(Duration duration) => _channel
      .invokeMethod('logAppLaunchTTI', {'durationMs': duration.inMilliseconds});

  // -- Network --

  /// Log the start of a network request.
  static Future<void> logNetworkRequest(HttpRequestInfo request) =>
      _channel.invokeMethod('logNetworkRequest', request.toMap());

  /// Log the completion of a network request.
  static Future<void> logNetworkResponse(HttpResponseInfo response) =>
      _channel.invokeMethod('logNetworkResponse', response.toMap());

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

  /// Information about how the previous app run ended, or null if the SDK
  /// has not started or the information is unavailable.
  static Future<PreviousRunInfo?> get previousRunInfo async {
    final result = await _channel.invokeMethod<Map>('getPreviousRunInfo');
    return result == null
        ? null
        : PreviousRunInfo.fromMap(result.cast<String, dynamic>());
  }

  /// Set the operation mode of the logger. Sleep mode reduces SDK activity
  /// to a minimum.
  static Future<void> setSleepMode(SleepMode mode) =>
      _channel.invokeMethod('setSleepMode', {'mode': mode.name});

  // -- Feature Flags --

  /// Record the exposure of a feature flag with a string [variant].
  static Future<void> setFeatureFlagExposure(String name, String variant) =>
      _channel.invokeMethod('setFeatureFlagExposure', {
        'name': name,
        'variant': variant,
      });

  /// Record the exposure of a feature flag with a boolean [variant].
  static Future<void> setFeatureFlagExposureBool(String name, bool variant) =>
      _channel.invokeMethod('setFeatureFlagExposure', {
        'name': name,
        'variant': variant,
      });

  // -- Fields --

  /// Add a persistent field that will be attached to all future logs.
  static Future<void> addField(String key, String value) =>
      _channel.invokeMethod('addField', {'key': key, 'value': value});

  /// Remove a previously added persistent field.
  static Future<void> removeField(String key) =>
      _channel.invokeMethod('removeField', {'key': key});

  // -- Entity --

  /// Sets the entity identifier used for backend correlation with this device.
  static Future<void> setEntityId(String entityId) =>
      _channel.invokeMethod('setEntityId', {'entityId': entityId});

  /// Clears the entity identifier used for backend correlation with this device.
  static Future<void> clearEntityId() => _channel.invokeMethod('clearEntityId');

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
  }) => _channel.invokeMethod('logReplayScreen', {
    'screen': encodedScreen,
    'duration': durationSeconds,
  });

  /// Start automatic session replay capture.
  ///
  /// This registers a persistent frame callback that captures the widget
  /// tree as wireframe rects at ~2Hz. On Android the encoded screen is sent
  /// to the Capture backend directly; on iOS the rects are exposed to the
  /// native session replay capture.
  static void startSessionReplay() {
    if (_replayActive) return;
    _replayActive = true;
    if (_replayCallbackRegistered) return;
    _replayCallbackRegistered = true;
    WidgetsBinding.instance.addPersistentFrameCallback((_) {
      if (_replayActive) _replayScheduler.onFrame();
    });
    WidgetsBinding.instance.scheduleFrame();
  }

  static void _captureReplay() {
    if (!_replayActive) return;

    if (defaultTargetPlatform == TargetPlatform.iOS) {
      _pushReplayRects();
      return;
    }

    final stopwatch = Stopwatch()..start();
    final encoded = FlutterReplayCapture.captureScreen();
    stopwatch.stop();
    logReplayScreen(
      encoded,
      durationSeconds: stopwatch.elapsedMicroseconds / 1000000.0,
    );
  }

  /// Stop automatic session replay capture.
  static void stopSessionReplay() {
    _replayActive = false;
    _replayScheduler.cancel();
    if (_lastPushedReplayRects != null) {
      _lastPushedReplayRects = null;
      _channel.invokeMethod('updateReplayRects', {'rects': Int32List(0)});
    }
  }

  static void _pushReplayRects() {
    final rects = flattenReplayRects(
      FlutterReplayCapture.captureRects(includeRoot: false),
    );
    if (listEquals(rects, _lastPushedReplayRects)) return;
    _lastPushedReplayRects = rects;
    _channel.invokeMethod('updateReplayRects', {'rects': rects});
  }

  // -- Error Reporting --

  /// Log [error] as a `DartError` error log.
  ///
  /// Uncaught errors are reported automatically when the SDK is started with
  /// `enableDartErrorReporting`; use this for errors caught by the app.
  static void reportError(Object error, StackTrace? stack) {
    DartErrorReporter.report(error, stack);
  }
}
