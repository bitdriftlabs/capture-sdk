import 'dart:ffi' show Abi;
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'fbs/report_generated.dart' as report;
import 'version.dart';

/// Device and app attributes provided by the host platform, used to populate
/// persisted Dart error reports.
@immutable
class ReportContext {
  final String sdkDirectory;
  final String? appId;
  final String? appVersion;
  final String? buildNumber;
  final int? versionCode;
  final String? osVersion;
  final String? osBrand;
  final String? osFingerprint;
  final String? osBuildVersion;
  final String? manufacturer;
  final String? model;
  final List<String>? cpuAbis;

  const ReportContext({
    required this.sdkDirectory,
    this.appId,
    this.appVersion,
    this.buildNumber,
    this.versionCode,
    this.osVersion,
    this.osBrand,
    this.osFingerprint,
    this.osBuildVersion,
    this.manufacturer,
    this.model,
    this.cpuAbis,
  });

  factory ReportContext.fromMap(Map<String, dynamic> map) => ReportContext(
    sdkDirectory: map['sdkDirectory'] as String,
    appId: map['appId'] as String?,
    appVersion: map['appVersion'] as String?,
    buildNumber: map['buildNumber'] as String?,
    versionCode: (map['versionCode'] as num?)?.toInt(),
    osVersion: map['osVersion'] as String?,
    osBrand: map['osBrand'] as String?,
    osFingerprint: map['osFingerprint'] as String?,
    osBuildVersion: map['osBuildVersion'] as String?,
    manufacturer: map['manufacturer'] as String?,
    model: map['model'] as String?,
    cpuAbis: (map['cpuAbis'] as List?)?.cast<String>(),
  );
}

/// Persists uncaught Dart errors as issue reports that are uploaded on the
/// next launch, attributed to the session in which they occurred.
class DartErrorReporter {
  DartErrorReporter._();

  static const _pendingDirectory = 'reports/flutter_pending';
  static const _watcherDirectory = 'reports/watcher';
  static const _previousSessionDirectory = 'reports/watcher/previous_session';
  static const _fileMarker = '_flutter_';
  static const _maxReportsPerRun = 10;

  // TODO: Use a dedicated Dart report type once the issue reporting schema has one.
  static const _reportType = report.ReportType.StrictModeViolation;

  static ReportContext? _context;
  static bool _handlersInstalled = false;
  static int _persistedThisRun = 0;
  static final _random = Random();

  /// Prepares the report directories. Must run before the native SDK starts.
  static void prepare(ReportContext context) {
    _context = context;
    Directory(_path(_pendingDirectory)).createSync(recursive: true);
    Directory(_path(_watcherDirectory)).createSync(recursive: true);
    _recoverUnprocessedReports();
  }

  /// Hooks into Flutter's framework and platform dispatcher error handlers,
  /// chaining to any previously installed handlers.
  static void installHandlers() {
    if (_handlersInstalled) return;
    _handlersInstalled = true;

    final previousFlutterHandler = FlutterError.onError;
    FlutterError.onError = (FlutterErrorDetails details) {
      persist(
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
      persist(error, stack);
      return previousPlatformHandler?.call(error, stack) ?? false;
    };
  }

  /// Hands reports persisted by previous runs to the native SDK one at a
  /// time, waiting up to [consumeTimeout] for each to be consumed. Reports
  /// that are not consumed after [maxAttempts] stay pending for the next
  /// launch. Returns the number of reports consumed.
  static Future<int> submitPendingReports({
    Duration consumeTimeout = const Duration(seconds: 5),
    Duration pollInterval = const Duration(milliseconds: 100),
    int maxAttempts = 3,
  }) async {
    if (_context == null) return 0;
    final pending = Directory(_path(_pendingDirectory));
    if (!pending.existsSync()) return 0;
    Directory(_path(_previousSessionDirectory)).createSync(recursive: true);

    final reports =
        pending
            .listSync()
            .whereType<File>()
            .where((file) => file.path.endsWith('.cap'))
            .toList()
          ..sort((a, b) => a.path.compareTo(b.path));

    var consumed = 0;
    for (final report in reports) {
      final submitted = File(
        _path('$_previousSessionDirectory/${_basename(report.path)}'),
      );
      var done = false;
      for (var attempt = 0; attempt < maxAttempts && !done; attempt++) {
        try {
          report.renameSync(submitted.path);
        } on FileSystemException catch (e) {
          debugPrint(
            'capture_flutter: failed to submit report ${report.path}: $e',
          );
          return consumed;
        }

        final deadline = DateTime.now().add(consumeTimeout);
        while (submitted.existsSync() && DateTime.now().isBefore(deadline)) {
          await Future<void>.delayed(pollInterval);
        }
        done = !submitted.existsSync();
        if (!done) {
          try {
            submitted.renameSync(report.path);
          } on FileSystemException catch (_) {
            done = !submitted.existsSync();
          }
        }
      }
      if (!done) return consumed;
      consumed++;
    }
    return consumed;
  }

  /// Serializes and persists a report for [error] synchronously.
  ///
  /// Returns the path of the persisted report, or null if the reporter has not
  /// been prepared, the per-run limit was reached or persisting failed.
  static String? persist(
    Object error,
    StackTrace? stack, {
    String? context,
    String? library,
  }) {
    final reportContext = _context;
    if (reportContext == null || _persistedThisRun >= _maxReportsPerRun) {
      return null;
    }
    _persistedThisRun++;
    try {
      final now = DateTime.now();
      final bytes = buildReport(
        reportContext,
        error,
        stack ?? StackTrace.empty,
        timestamp: now,
        errorContext: context,
        library: library,
      );
      final suffix = _random.nextInt(1 << 32).toRadixString(16);
      final name =
          '${now.millisecondsSinceEpoch}${_fileMarker}strict_mode_violation_$suffix.cap';
      final temp = File(_path('$_pendingDirectory/.$name.tmp'));
      temp.writeAsBytesSync(bytes, flush: true);
      return temp.renameSync(_path('$_pendingDirectory/$name')).path;
    } catch (e) {
      debugPrint('capture_flutter: failed to persist Dart error report: $e');
      return null;
    }
  }

  @visibleForTesting
  static Uint8List buildReport(
    ReportContext context,
    Object error,
    StackTrace stack, {
    required DateTime timestamp,
    String? errorContext,
    String? library,
  }) {
    final micros = timestamp.microsecondsSinceEpoch;
    final reason = StringBuffer(error.toString());
    if (errorContext != null) reason.write('\nContext: $errorContext');
    if (library != null) reason.write('\nLibrary: $library');

    final builder = report.ReportObjectBuilder(
      sdk: report.SdkinfoObjectBuilder(
        id: 'io.bitdrift.capture-flutter',
        version: captureFlutterVersion,
      ),
      type: _reportType,
      appMetrics: report.AppMetricsObjectBuilder(
        appId: context.appId,
        version: context.appVersion,
        buildNumber: report.AppBuildNumberObjectBuilder(
          versionCode: context.versionCode,
          cfBundleVersion: Platform.isIOS ? context.buildNumber : null,
        ),
        processId: pid,
        runningState: 'foreground',
      ),
      deviceMetrics: report.DeviceMetricsObjectBuilder(
        time: report.TimestampObjectBuilder(
          seconds: micros ~/ Duration.microsecondsPerSecond,
          nanos: (micros % Duration.microsecondsPerSecond) * 1000,
        ),
        timezone: timestamp.timeZoneName,
        arch: _architecture(),
        manufacturer: context.manufacturer,
        model: context.model,
        osBuild: report.OsbuildObjectBuilder(
          version: context.osVersion,
          brand: context.osBrand,
          fingerprint: context.osFingerprint,
          kernOsversion: context.osBuildVersion,
        ),
        platform: Platform.isIOS
            ? report.Platform.iOS
            : Platform.isAndroid
            ? report.Platform.Android
            : report.Platform.Unknown,
        cpuAbis: context.cpuAbis,
      ),
      errors: [
        report.ErrorObjectBuilder(
          name: error.runtimeType.toString(),
          reason: reason.toString(),
          stackTrace: parseStackTrace(stack),
        ),
      ],
    );
    return builder.toBytes();
  }

  @visibleForTesting
  static List<report.FrameObjectBuilder> parseStackTrace(StackTrace stack) {
    final frames = <report.FrameObjectBuilder>[];
    for (final line in stack.toString().split('\n')) {
      final trimmed = line.trim();
      if (trimmed.isEmpty || trimmed == '<asynchronous suspension>') continue;
      frames.add(_parseFrame(trimmed, frames.length));
    }
    return frames;
  }

  static final _framePattern = RegExp(
    r'^#\d+\s+(.+?)\s+\((.+?)(?::(\d+))?(?::(\d+))?\)$',
  );

  static final _classPattern = RegExp(r'^_*[A-Z][^.]*\.');

  static report.FrameObjectBuilder _parseFrame(String line, int index) {
    final match = _framePattern.firstMatch(line);
    if (match == null) {
      return report.FrameObjectBuilder(
        type: report.FrameType.Unknown,
        symbolName: line,
        originalIndex: index,
      );
    }

    final member = match.group(1)!;
    final uri = match.group(2)!;
    final separator = _classPattern.hasMatch(member) ? member.indexOf('.') : -1;
    return report.FrameObjectBuilder(
      type: report.FrameType.Unknown,
      className: separator > 0 ? member.substring(0, separator) : null,
      symbolName: separator > 0 ? member.substring(separator + 1) : member,
      sourceFile: report.SourceFileObjectBuilder(
        path: uri,
        line: int.tryParse(match.group(3) ?? ''),
        column: int.tryParse(match.group(4) ?? ''),
      ),
      originalIndex: index,
      inApp: uri.startsWith('package:') && !uri.startsWith('package:flutter/'),
    );
  }

  static report.Architecture _architecture() {
    switch (Abi.current()) {
      case Abi.androidArm64:
      case Abi.iosArm64:
        return report.Architecture.arm64;
      case Abi.androidArm:
        return report.Architecture.arm32;
      case Abi.androidIA32:
        return report.Architecture.x86;
      case Abi.androidX64:
      case Abi.iosX64:
        return report.Architecture.x86_64;
      default:
        return report.Architecture.Unknown;
    }
  }

  static void _recoverUnprocessedReports() {
    final previous = Directory(_path(_previousSessionDirectory));
    if (!previous.existsSync()) return;
    for (final file in previous.listSync().whereType<File>()) {
      final name = _basename(file.path);
      if (!name.contains(_fileMarker) || !name.endsWith('.cap')) continue;
      try {
        file.renameSync(_path('$_pendingDirectory/$name'));
      } on FileSystemException catch (_) {}
    }
  }

  static String _path(String relative) => '${_context!.sdkDirectory}/$relative';

  static String _basename(String path) =>
      path.substring(path.lastIndexOf('/') + 1);
}

/// Initializes the Dart error reporter using attributes fetched from the
/// host platform.
Future<void> prepareDartErrorReporter(MethodChannel channel) async {
  final result = await channel.invokeMethod<Map>('getReportContext');
  if (result == null) return;
  DartErrorReporter.prepare(
    ReportContext.fromMap(result.cast<String, dynamic>()),
  );
}
