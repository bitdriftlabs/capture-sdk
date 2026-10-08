import 'package:capture_flutter/src/session_replay.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

List<ReplayRect> capture() =>
    FlutterReplayCapture.captureRects(includeRoot: false);

Map<ReplayType, int> histogram(List<ReplayRect> rects) {
  final result = <ReplayType, int>{};
  for (final rect in rects) {
    result[rect.type] = (result[rect.type] ?? 0) + 1;
  }
  return result;
}

Rect bounds(ReplayRect rect) => Rect.fromLTWH(
  rect.x.toDouble(),
  rect.y.toDouble(),
  rect.width.toDouble(),
  rect.height.toDouble(),
);

void main() {
  testWidgets('a button produces a single button rect and its label', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: ElevatedButton(onPressed: () {}, child: const Text('Tap')),
          ),
        ),
      ),
    );

    final rects = capture();
    expect(histogram(rects)[ReplayType.button], 1);
    expect(histogram(rects)[ReplayType.label], 1);
    final button = rects.firstWhere((r) => r.type == ReplayType.button);
    expect(
      rects.where(
        (r) =>
            r.type == ReplayType.view &&
            bounds(button).overlaps(bounds(r)) &&
            r.width < 800,
      ),
      isEmpty,
    );
  });

  testWidgets('icon buttons are not duplicated', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          appBar: AppBar(
            actions: [
              IconButton(icon: const Icon(Icons.settings), onPressed: () {}),
            ],
          ),
        ),
      ),
    );

    expect(histogram(capture())[ReplayType.button], 1);
  });

  testWidgets('routes covered by an opaque route are not captured', (
    tester,
  ) async {
    final navigator = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigator,
        home: Scaffold(
          body: Column(
            children: List.generate(
              5,
              (i) => ElevatedButton(onPressed: () {}, child: Text('Home $i')),
            ),
          ),
        ),
      ),
    );
    expect(histogram(capture())[ReplayType.button], 5);

    navigator.currentState!.push(
      MaterialPageRoute<void>(
        builder: (_) => const Scaffold(body: Center(child: Text('Second'))),
      ),
    );
    await tester.pumpAndSettle();

    final rects = capture();
    expect(histogram(rects)[ReplayType.button], isNull);
    expect(histogram(rects)[ReplayType.label], 1);
  });

  testWidgets('invisible subtrees are not captured', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Column(
          children: [
            Opacity(opacity: 0, child: Text('transparent')),
            Offstage(child: Text('offstage')),
            Visibility(visible: false, child: Text('hidden')),
            Text('visible'),
          ],
        ),
      ),
    );

    expect(histogram(capture()), {ReplayType.label: 1});
  });

  testWidgets('scrolled content is clipped to the viewport', (tester) async {
    tester.view.physicalSize = const Size(400, 400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Padding(
            padding: const EdgeInsets.only(top: 100),
            child: ListView(
              children: List.generate(
                50,
                (i) => SizedBox(height: 50, child: Text('Item $i')),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.drag(find.byType(ListView), const Offset(0, -175));
    await tester.pumpAndSettle();

    final labels = capture().where((r) => r.type == ReplayType.label).toList();
    expect(labels, isNotEmpty);
    for (final label in labels) {
      expect(label.y, greaterThanOrEqualTo(100));
      expect(label.y + label.height, lessThanOrEqualTo(400));
    }
    expect(labels.length, lessThanOrEqualTo(7));
  });

  testWidgets('dialogs keep the route underneath visible', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: ElevatedButton(
                onPressed: () => showDialog<void>(
                  context: context,
                  builder: (_) => const AlertDialog(title: Text('Dialog')),
                ),
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();

    final rects = capture();
    expect(histogram(rects)[ReplayType.button], 1);
    expect(rects.where((r) => r.type == ReplayType.label), hasLength(2));
  });

  testWidgets('scaled content reports its painted size', (tester) async {
    tester.view.physicalSize = const Size(400, 400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(
        home: Align(
          alignment: Alignment.topLeft,
          child: Transform.scale(
            scale: 0.5,
            alignment: Alignment.topLeft,
            child: SizedBox(width: 200, height: 100, child: Text('scaled')),
          ),
        ),
      ),
    );

    final label = capture().singleWhere((r) => r.type == ReplayType.label);
    expect(label.width, lessThanOrEqualTo(100));
  });

  testWidgets('controls produce a single rect without inner layers', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            children: [
              const TextField(decoration: InputDecoration(labelText: 'Name')),
              Switch(value: false, onChanged: (_) {}),
            ],
          ),
        ),
      ),
    );

    final types = histogram(capture());
    expect(types[ReplayType.textInput], 1);
    expect(types[ReplayType.switchOff], 1);
    expect(types[ReplayType.view], 1);
  });

  testWidgets('kept alive list items outside the viewport are skipped', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(
        home: ListView.builder(
          itemCount: 100,
          addAutomaticKeepAlives: true,
          itemBuilder: (_, i) =>
              _KeepAlive(child: SizedBox(height: 100, child: Text('Item $i'))),
        ),
      ),
    );
    await tester.drag(find.byType(ListView), const Offset(0, -2000));
    await tester.pumpAndSettle();

    final labels = capture().where((r) => r.type == ReplayType.label);
    expect(labels.length, lessThanOrEqualTo(5));
  });

  testWidgets('both routes are captured mid transition', (tester) async {
    final navigator = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigator,
        home: const Scaffold(body: Text('First')),
      ),
    );
    navigator.currentState!.push(
      MaterialPageRoute<void>(
        builder: (_) => const Scaffold(body: Text('Second')),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(histogram(capture())[ReplayType.label], 2);
    await tester.pumpAndSettle();
    expect(histogram(capture())[ReplayType.label], 1);
  });

  testWidgets('capturing a large tree stays fast', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: Column(
              children: List.generate(
                300,
                (i) => Card(
                  child: ListTile(
                    title: Text('Title $i'),
                    subtitle: Text('Subtitle $i'),
                    trailing: IconButton(
                      icon: const Icon(Icons.add),
                      onPressed: () {},
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );

    capture();
    final stopwatch = Stopwatch()..start();
    final rects = capture();
    stopwatch.stop();
    expect(
      rects.where((r) => r.type == ReplayType.button).length,
      lessThan(20),
    );
    expect(stopwatch.elapsedMilliseconds, lessThan(50));
  });
}

class _KeepAlive extends StatefulWidget {
  const _KeepAlive({required this.child});

  final Widget child;

  @override
  State<_KeepAlive> createState() => _KeepAliveState();
}

class _KeepAliveState extends State<_KeepAlive>
    with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return widget.child;
  }
}
