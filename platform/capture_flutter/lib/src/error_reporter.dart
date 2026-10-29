import 'package:flutter/foundation.dart';

/// Emits a log for an error, given its fields.
typedef DartErrorLogger = void Function(Map<String, String> fields);

/// Logs uncaught Dart errors as `DartError` error logs.
///
/// Dart errors don't terminate the app, so they are logged in the current
/// session rather than reported as fatal issues.
class DartErrorReporter {
  DartErrorReporter._();

  static const _maxErrorsPerRun = 10;

  static DartErrorLogger? _logger;
  static bool _handlersInstalled = false;
  static int _reportedThisRun = 0;

  /// Hooks into Flutter's framework and platform dispatcher error handlers,
  /// chaining to any previously installed handlers.
  static void installHandlers(DartErrorLogger logger) {
    _logger = logger;
    if (_handlersInstalled) return;
    _handlersInstalled = true;

    final previousFlutterHandler = FlutterError.onError;
    FlutterError.onError = (FlutterErrorDetails details) {
      report(
        details.exception,
        details.stack,
        context: details.context?.toDescription(),
        library: details.library,
      );
      if (previousFlutterHandler != null) {
        previousFlutterHandler(details);
      } else {
        FlutterError.presentError(details);
      }
    };

    final previousPlatformHandler = PlatformDispatcher.instance.onError;
    PlatformDispatcher.instance.onError = (Object error, StackTrace stack) {
      report(error, stack);
      return previousPlatformHandler?.call(error, stack) ?? false;
    };
  }

  /// Logs [error], up to a limit per app run. Returns whether it was logged.
  static bool report(
    Object error,
    StackTrace? stack, {
    String? context,
    String? library,
  }) {
    final logger = _logger;
    if (logger == null || _reportedThisRun >= _maxErrorsPerRun) return false;
    _reportedThisRun++;
    logger(errorFields(error, stack, context: context, library: library));
    return true;
  }

  @visibleForTesting
  static Map<String, String> errorFields(
    Object error,
    StackTrace? stack, {
    String? context,
    String? library,
  }) => {
    '_error': error.runtimeType.toString(),
    '_error_details': error.toString(),
    ...stackTraceFields(error, stack),
    if (context != null) '_error_context': context,
    if (library != null) '_error_library': library,
  };

  /// The `_stacktrace` field for [stack], falling back to the stack trace
  /// recorded by [error] when it is a Dart [Error].
  static Map<String, String> stackTraceFields(
    Object? error,
    StackTrace? stack,
  ) {
    final trace = stack ?? (error is Error ? error.stackTrace : null);
    if (trace == null || trace == StackTrace.empty) return const {};
    return {'_stacktrace': trace.toString()};
  }

  @visibleForTesting
  static void reset() {
    _logger = null;
    _reportedThisRun = 0;
  }
}
