import 'dart:io';

import 'package:capture_flutter/capture_flutter.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_capture_example/main.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:integration_test/integration_test.dart';

/// End-to-end checks that run the example app against the native Capture SDK.
///
/// Run on a device or simulator with:
///
/// ```sh
/// flutter drive --driver=test_driver/integration_test.dart \
///   --target=integration_test/capture_test.dart
/// ```
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  late HttpServer server;
  late Uri baseUri;

  setUpAll(() async {
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    baseUri = Uri.parse('http://${server.address.host}:${server.port}');
    server.listen((request) async {
      final response = request.response;
      switch (request.uri.path) {
        case '/ok':
          response.write('hello');
        case '/missing':
          response.statusCode = HttpStatus.notFound;
        case '/stream':
          // Keep the body open so the client can cancel mid-response.
          response.bufferOutput = false;
          response.add(List.filled(16, 1));
          await response.flush();
          await Future<void>.delayed(const Duration(seconds: 5));
      }
      try {
        await response.close();
      } on Object {
        // The client canceled the response.
      }
    });

    // The native SDK can only be started once per process.
    final started = await Capture.start(
      apiKey: 'integration-test',
      enableSessionReplay: true,
      enableFatalIssueReporting: false,
    );
    expect(started, isTrue);
  });

  tearDownAll(() => server.close(force: true));

  group('session replay', () {
    testWidgets('captures the example app and can be restarted', (
      tester,
    ) async {
      await tester.pumpWidget(const CaptureExampleApp());
      await tester.pumpAndSettle();
      expect(find.text('Log Info'), findsOneWidget);

      final rects = FlutterReplayCapture.captureRects(includeRoot: false);
      expect(rects, isNotEmpty);
      if (defaultTargetPlatform == TargetPlatform.android) {
        // Replay screens are sent to the native SDK directly on Android.
        await Capture.logReplayScreen(FlutterReplayCapture.captureScreen());
      }

      Capture.stopSessionReplay();
      await tester.pumpAndSettle();
      expect(Capture.startSessionReplay(), isTrue);
      await tester.pump(const Duration(milliseconds: 300));

      await tester.tap(find.text('Log Info'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Log Error'));
      await tester.pumpAndSettle();
      expect(find.text('Error Logged'), findsOneWidget);
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();
      expect(find.text('Error Logged'), findsNothing);
    });
  });

  group('network logging', () {
    late CaptureHttpClient client;

    setUp(() => client = CaptureHttpClient());
    tearDown(() => client.close());

    testWidgets('logs successful and failed responses', (tester) async {
      final ok = await client.get(baseUri.resolve('/ok'));
      expect(ok.statusCode, HttpStatus.ok);
      expect(ok.body, 'hello');

      final missing = await client.get(baseUri.resolve('/missing'));
      expect(missing.statusCode, HttpStatus.notFound);
    });

    testWidgets('canceling a response body completes the request', (
      tester,
    ) async {
      final request = await client.send(
        http.Request('GET', baseUri.resolve('/stream')),
      );
      expect(request.statusCode, HttpStatus.ok);
      expect(
        await request.stream.first.timeout(const Duration(seconds: 2)),
        hasLength(16),
      );
    });

    testWidgets('unreachable hosts are reported and rethrown', (tester) async {
      final closed = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
      final port = closed.port;
      await closed.close();

      await expectLater(
        client.get(Uri.parse('http://127.0.0.1:$port/')),
        throwsA(isA<Exception>()),
      );
    });

    testWidgets('the native SDK accepts every response result', (tester) async {
      for (final result in HttpResult.values) {
        final request = HttpRequestInfo.fromUri('GET', baseUri.resolve('/ok'));
        await Capture.logNetworkRequest(request);
        await Capture.logNetworkResponse(
          HttpResponseInfo(
            request: request,
            result: result,
            statusCode: result == HttpResult.success ? 200 : null,
            error: result == HttpResult.failure
                ? const SocketException('unreachable')
                : null,
            responseBodyBytesReceivedCount: 0,
          ),
        );
      }
    });
  });
}
