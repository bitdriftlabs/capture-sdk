import 'package:capture_flutter/capture_flutter.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  const channel = MethodChannel('io.bitdrift.capture_flutter');
  late List<MethodCall> calls;

  setUp(() {
    calls = [];
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

  Future<void> start({required bool enableSessionReplay}) => Capture.start(
    apiKey: 'key',
    enableSessionReplay: enableSessionReplay,
    enableDartErrorReporting: false,
  );

  Future<void> pumpApp(WidgetTester tester) => tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: Center(
          child: ElevatedButton(onPressed: () {}, child: const Text('Tap')),
        ),
      ),
    ),
  );

  /// Renders a frame and waits long enough for the replay scheduler to
  /// capture it.
  Future<void> settle(WidgetTester tester) async {
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
  }

  List<Int32List> pushedRects() => calls
      .where((c) => c.method == 'updateReplayRects')
      .map((c) => (c.arguments as Map)['rects'] as Int32List)
      .toList();

  Iterable<MethodCall> replayScreens() =>
      calls.where((c) => c.method == 'logReplayScreen');

  /// Runs [body] on [platform], stopping replay so no capture timers are
  /// left pending.
  void replayTest(
    String description,
    TargetPlatform platform,
    WidgetTesterCallback body,
  ) => testWidgets(description, (tester) async {
    try {
      await body(tester);
    } finally {
      Capture.stopSessionReplay();
    }
  }, variant: TargetPlatformVariant.only(platform));

  group('iOS', () {
    const iOS = TargetPlatform.iOS;

    replayTest('pushes rects when started with session replay', iOS, (
      tester,
    ) async {
      await pumpApp(tester);
      await start(enableSessionReplay: true);
      await settle(tester);

      final rects = pushedRects();
      expect(rects, hasLength(1));
      expect(rects.single, isNotEmpty);
      expect(replayScreens(), isEmpty);
    });

    replayTest(
      'rejects runtime activation without native session replay',
      iOS,
      (tester) async {
        await pumpApp(tester);
        await start(enableSessionReplay: false);

        expect(Capture.startSessionReplay(), isFalse);
        await settle(tester);
        await tester.pump(const Duration(seconds: 2));

        expect(pushedRects(), isEmpty);
      },
    );

    replayTest('rejects activation before the SDK has started', iOS, (
      tester,
    ) async {
      calls.clear();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            calls.add(call);
            return call.method == 'start' ? false : null;
          });
      await pumpApp(tester);
      await start(enableSessionReplay: true);

      expect(Capture.isStarted, isFalse);
      expect(Capture.startSessionReplay(), isFalse);
      await settle(tester);
      expect(pushedRects(), isEmpty);
    });

    replayTest('stop clears rects and restart captures a static screen', iOS, (
      tester,
    ) async {
      await pumpApp(tester);
      await start(enableSessionReplay: true);
      await settle(tester);
      expect(pushedRects(), hasLength(1));

      Capture.stopSessionReplay();
      await tester.pump();
      expect(pushedRects().last, isEmpty);

      // Nothing on screen changes, so a frame must be requested by the
      // activation itself.
      expect(tester.binding.hasScheduledFrame, isFalse);
      expect(Capture.startSessionReplay(), isTrue);
      expect(tester.binding.hasScheduledFrame, isTrue);
      await settle(tester);

      expect(pushedRects(), hasLength(3));
      expect(pushedRects().last, isNotEmpty);
    });

    replayTest('unchanged screens are not pushed again', iOS, (tester) async {
      await pumpApp(tester);
      await start(enableSessionReplay: true);
      await settle(tester);
      await tester.pump(const Duration(seconds: 1));
      await settle(tester);

      expect(pushedRects(), hasLength(1));
    });
  });

  group('Android', () {
    const android = TargetPlatform.android;

    replayTest(
      'logs replay screens when started with session replay',
      android,
      (tester) async {
        await pumpApp(tester);
        await start(enableSessionReplay: true);
        await settle(tester);

        expect(replayScreens(), hasLength(1));
        final screen =
            (replayScreens().single.arguments as Map)['screen'] as Uint8List;
        expect(screen, isNotEmpty);
        expect(pushedRects(), isEmpty);
      },
    );

    replayTest('supports runtime activation', android, (tester) async {
      await pumpApp(tester);
      await start(enableSessionReplay: false);
      await settle(tester);
      expect(replayScreens(), isEmpty);

      expect(Capture.startSessionReplay(), isTrue);
      expect(tester.binding.hasScheduledFrame, isTrue);
      await settle(tester);

      expect(replayScreens(), hasLength(1));
    });

    replayTest('stops capturing after stopSessionReplay', android, (
      tester,
    ) async {
      await pumpApp(tester);
      await start(enableSessionReplay: true);
      await settle(tester);
      expect(replayScreens(), hasLength(1));

      Capture.stopSessionReplay();
      await tester.tap(find.byType(ElevatedButton));
      await tester.pumpAndSettle();
      await tester.pump(const Duration(seconds: 2));

      expect(replayScreens(), hasLength(1));
    });
  });
}
