import 'package:capture_flutter/capture_flutter.dart';
import 'package:capture_flutter/src/error_reporter.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('io.bitdrift.capture_flutter');
  late List<MethodCall> calls;

  setUp(() {
    calls = [];
    DartErrorReporter.reset();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return call.method == 'start' ? true : null;
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  Map<String, dynamic> lastLog() =>
      (calls.lastWhere((c) => c.method == 'log').arguments as Map)
          .cast<String, dynamic>();

  test('error fields carry type, details, stack, context and library', () {
    final fields = DartErrorReporter.errorFields(
      StateError('boom'),
      StackTrace.fromString('#0      main (package:example/main.dart:3:1)'),
      context: 'while handling a gesture',
      library: 'gesture',
    );

    expect(fields, {
      '_error': 'StateError',
      '_error_details': 'Bad state: boom',
      '_stacktrace': '#0      main (package:example/main.dart:3:1)',
      '_error_context': 'while handling a gesture',
      '_error_library': 'gesture',
    });
  });

  test('uncaught framework errors are logged as DartError', () async {
    final previousHandler = FlutterError.onError;
    final forwarded = <FlutterErrorDetails>[];
    FlutterError.onError = forwarded.add;
    addTearDown(() => FlutterError.onError = previousHandler);

    await Capture.start(apiKey: 'test');
    FlutterError.reportError(
      FlutterErrorDetails(
        exception: StateError('boom'),
        stack: StackTrace.current,
        library: 'gesture',
        context: ErrorDescription('while handling a gesture'),
      ),
    );
    await pumpEventQueue();

    final log = lastLog();
    expect(log['level'], 'error');
    expect(log['message'], 'DartError');
    final fields = (log['fields'] as Map).cast<String, String>();
    expect(fields['_error'], 'StateError');
    expect(fields['_error_details'], 'Bad state: boom');
    expect(fields['_error_library'], 'gesture');
    expect(fields['_error_context'], 'while handling a gesture');
    expect(fields['_stacktrace'], isNotEmpty);
    expect(forwarded, hasLength(1));
  });

  test('caught errors can be reported manually', () async {
    await Capture.start(apiKey: 'test');
    Capture.reportError(const FormatException('bad'), StackTrace.current);
    await pumpEventQueue();

    expect(lastLog()['message'], 'DartError');
    expect((lastLog()['fields'] as Map)['_error'], 'FormatException');
  });

  test('errors are capped per run', () async {
    await Capture.start(apiKey: 'test');
    for (var i = 0; i < 20; i++) {
      Capture.reportError(StateError('boom $i'), null);
    }
    await pumpEventQueue();

    expect(calls.where((c) => c.method == 'log'), hasLength(10));
  });

  test('errors without a stack trace use the one they recorded', () async {
    await Capture.start(apiKey: 'test');
    try {
      throw StateError('thrown');
    } on StateError catch (error) {
      await Capture.logError('failed', error: error);
    }

    final fields = (lastLog()['fields'] as Map).cast<String, String>();
    expect(fields['_stacktrace'], contains('error_reporter_test.dart'));
  });
}
