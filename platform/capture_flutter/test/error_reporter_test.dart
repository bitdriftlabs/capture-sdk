import 'dart:async';
import 'dart:io';

import 'package:capture_flutter/src/error_reporter.dart';
import 'package:capture_flutter/src/fbs/report_generated.dart' as report;
import 'package:flutter_test/flutter_test.dart';

void main() {
  const context = ReportContext(
    sdkDirectory: '/unused',
    appId: 'io.bitdrift.example',
    appVersion: '1.2.3',
    versionCode: 42,
    osVersion: '18.2',
    manufacturer: 'Apple',
    model: 'iPhone17,1',
  );

  test('buildReport produces a parseable report', () {
    final stack = StackTrace.fromString(
      '#0      HomePage.build.<anonymous closure> (package:example/main.dart:12:5)\n'
      '#1      _InkResponseState.handleTap (package:flutter/src/material/ink_well.dart:1204:21)\n'
      '<asynchronous suspension>\n'
      '#2      main (file:///app/lib/main.dart:3:1)\n',
    );

    final bytes = DartErrorReporter.buildReport(
      context,
      StateError('boom'),
      stack,
      timestamp: DateTime.fromMicrosecondsSinceEpoch(1700000000123456),
      errorContext: 'while handling a gesture',
    );
    final parsed = report.Report(bytes);

    expect(parsed.type, report.ReportType.StrictModeViolation);
    expect(parsed.sdk!.id, 'io.bitdrift.capture-flutter');
    expect(parsed.appMetrics!.appId, 'io.bitdrift.example');
    expect(parsed.appMetrics!.buildNumber!.versionCode, 42);
    expect(parsed.deviceMetrics!.time!.seconds, 1700000000);
    expect(parsed.deviceMetrics!.time!.nanos, 123456000);
    expect(parsed.deviceMetrics!.model, 'iPhone17,1');

    final error = parsed.errors!.single;
    expect(error.name, 'StateError');
    expect(error.reason, contains('boom'));
    expect(error.reason, contains('while handling a gesture'));

    final frames = error.stackTrace!;
    expect(frames, hasLength(3));
    expect(frames[0].className, 'HomePage');
    expect(frames[0].symbolName, 'build.<anonymous closure>');
    expect(frames[0].sourceFile!.path, 'package:example/main.dart');
    expect(frames[0].sourceFile!.line, 12);
    expect(frames[0].sourceFile!.column, 5);
    expect(frames[0].inApp, isTrue);
    expect(frames[1].inApp, isFalse);
    expect(frames[2].symbolName, 'main');
    expect(frames[2].originalIndex, 2);
  });

  test('unparseable frames are kept verbatim', () {
    final frames = DartErrorReporter.parseStackTrace(
      StackTrace.fromString('*** *** ***\npc 0x1234 /data/app/libapp.so'),
    );
    expect(frames, hasLength(2));
  });

  group('submitPendingReports', () {
    late Directory dir;
    late Directory watched;

    setUp(() {
      dir = Directory.systemTemp.createTempSync('capture_flutter_test');
      watched = Directory('${dir.path}/reports/watcher/previous_session');
      DartErrorReporter.prepare(ReportContext(sdkDirectory: dir.path));
    });

    tearDown(() => dir.deleteSync(recursive: true));

    test(
      'submits reports one at a time as the watcher consumes them',
      () async {
        expect(Directory('${dir.path}/reports/watcher').existsSync(), isTrue);
        expect(
          DartErrorReporter.persist(StateError('a'), StackTrace.current),
          isNotNull,
        );
        expect(
          DartErrorReporter.persist(StateError('b'), StackTrace.current),
          isNotNull,
        );

        final consumedNames = <String>[];
        var maxInFlight = 0;
        final watcher = Timer.periodic(const Duration(milliseconds: 5), (_) {
          if (!watched.existsSync()) return;
          final files = watched.listSync().whereType<File>().toList();
          if (files.length > maxInFlight) maxInFlight = files.length;
          for (final file in files) {
            consumedNames.add(
              report.Report(file.readAsBytesSync()).errors!.single.reason!,
            );
            file.deleteSync();
          }
        });
        addTearDown(watcher.cancel);

        final consumed = await DartErrorReporter.submitPendingReports(
          pollInterval: const Duration(milliseconds: 1),
        );

        expect(consumed, 2);
        expect(maxInFlight, 1);
        expect(consumedNames, ['Bad state: a', 'Bad state: b']);
        expect(
          Directory('${dir.path}/reports/flutter_pending').listSync(),
          isEmpty,
        );
      },
    );

    test(
      'reports that are not consumed stay pending for the next launch',
      () async {
        DartErrorReporter.persist(StateError('a'), StackTrace.current);

        final consumed = await DartErrorReporter.submitPendingReports(
          consumeTimeout: const Duration(milliseconds: 10),
          pollInterval: const Duration(milliseconds: 1),
        );

        expect(consumed, 0);
        expect(watched.listSync(), isEmpty);
        expect(
          Directory('${dir.path}/reports/flutter_pending').listSync(),
          hasLength(1),
        );
      },
    );

    test('reports stranded in the watched directory are recovered', () async {
      final path = DartErrorReporter.persist(
        StateError('a'),
        StackTrace.current,
      )!;
      watched.createSync(recursive: true);
      File(path).renameSync('${watched.path}/${path.split('/').last}');

      DartErrorReporter.prepare(ReportContext(sdkDirectory: dir.path));

      expect(watched.listSync(), isEmpty);
      expect(
        Directory('${dir.path}/reports/flutter_pending').listSync(),
        hasLength(1),
      );
    });
  });
}
