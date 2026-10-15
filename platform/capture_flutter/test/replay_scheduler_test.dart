import 'package:capture_flutter/src/replay_scheduler.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late List<Duration> captures;
  late Duration now;
  late ReplayCaptureScheduler scheduler;

  Future<void> advance(WidgetTester tester, Duration duration) async {
    const step = Duration(milliseconds: 4);
    final end = now + duration;
    while (now < end) {
      now += step;
      await tester.pump(step);
    }
  }

  Future<void> frames(WidgetTester tester, Duration total) async {
    const frame = Duration(milliseconds: 16);
    var elapsed = Duration.zero;
    while (elapsed < total) {
      scheduler.onFrame();
      await advance(tester, frame);
      elapsed += frame;
    }
  }

  setUp(() {
    captures = [];
    now = Duration.zero;
    scheduler = ReplayCaptureScheduler(() => captures.add(now));
  });

  testWidgets('captures once frames settle', (tester) async {
    await frames(tester, const Duration(milliseconds: 300));
    expect(captures, isEmpty);

    await advance(tester, const Duration(milliseconds: 200));
    expect(captures, hasLength(1));
    expect(
      captures.single,
      greaterThanOrEqualTo(const Duration(milliseconds: 300)),
    );
    scheduler.cancel();
  });

  testWidgets('continuous animations are captured at least every max delay', (
    tester,
  ) async {
    await frames(tester, const Duration(seconds: 3));
    expect(captures.length, inInclusiveRange(2, 3));
    scheduler.cancel();
  });

  testWidgets('continuous animations are captured at most max delay apart', (
    tester,
  ) async {
    await frames(tester, const Duration(seconds: 5));
    scheduler.cancel();

    // Timers fire on the next pump step, so allow one step of slack.
    const slack = Duration(milliseconds: 4);
    expect(captures, hasLength(greaterThanOrEqualTo(4)));
    expect(captures.first, lessThanOrEqualTo(scheduler.maxDelay + slack));
    for (var i = 1; i < captures.length; i++) {
      final gap = captures[i] - captures[i - 1];
      expect(gap, lessThanOrEqualTo(scheduler.maxDelay + slack));
      expect(gap, greaterThanOrEqualTo(scheduler.minInterval));
    }
  });

  testWidgets('max delay below the min interval still respects the interval', (
    tester,
  ) async {
    scheduler = ReplayCaptureScheduler(
      () => captures.add(now),
      maxDelay: const Duration(milliseconds: 300),
    );
    await frames(tester, const Duration(seconds: 3));
    scheduler.cancel();

    const slack = Duration(milliseconds: 4);
    expect(captures, hasLength(greaterThanOrEqualTo(5)));
    for (var i = 1; i < captures.length; i++) {
      final gap = captures[i] - captures[i - 1];
      expect(gap, greaterThanOrEqualTo(scheduler.minInterval));
      expect(gap, lessThanOrEqualTo(scheduler.minInterval + slack));
    }
  });

  testWidgets('frames that stop during the cooldown settle after it', (
    tester,
  ) async {
    scheduler.onFrame();
    await advance(tester, const Duration(milliseconds: 200));
    expect(captures, hasLength(1));

    // A single frame during the cooldown is captured once it settles, well
    // before the remaining max-delay budget runs out.
    scheduler.onFrame();
    await advance(tester, const Duration(milliseconds: 700));
    expect(captures, hasLength(2));
    expect(
      captures[1] - captures[0],
      lessThanOrEqualTo(
        scheduler.minInterval +
            scheduler.settleDelay +
            const Duration(milliseconds: 4),
      ),
    );
    scheduler.cancel();
  });

  testWidgets('cancel drops pending captures', (tester) async {
    await frames(tester, const Duration(milliseconds: 100));
    scheduler.cancel();
    await advance(tester, const Duration(seconds: 2));
    expect(captures, isEmpty);
  });

  testWidgets('frames during the cooldown produce a trailing capture', (
    tester,
  ) async {
    scheduler.onFrame();
    await advance(tester, const Duration(milliseconds: 200));
    expect(captures, hasLength(1));

    await frames(tester, const Duration(milliseconds: 100));
    await advance(tester, const Duration(seconds: 1));
    expect(captures, hasLength(2));
    expect(
      captures[1] - captures[0],
      greaterThanOrEqualTo(const Duration(milliseconds: 500)),
    );
    scheduler.cancel();
  });

  testWidgets('no frames means no captures', (tester) async {
    await advance(tester, const Duration(seconds: 2));
    expect(captures, isEmpty);
    scheduler.cancel();
  });
}
