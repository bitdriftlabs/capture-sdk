import 'dart:async';

import 'package:capture_flutter/capture_flutter.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('io.bitdrift.capture_flutter');
  late List<MethodCall> calls;
  late StreamController<List<int>> body;

  setUp(() {
    calls = [];
    body = StreamController<List<int>>();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return null;
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  Future<void> flush() => Future<void>.delayed(Duration.zero);

  List<Map<String, dynamic>> argsOf(String method) => calls
      .where((c) => c.method == method)
      .map((c) => (c.arguments as Map).cast<String, dynamic>())
      .toList();

  Map<String, dynamic> singleResponse() => argsOf('logNetworkResponse').single;

  CaptureHttpClient clientWith(int statusCode) => CaptureHttpClient(
    MockClient.streaming(
      (request, _) async => http.StreamedResponse(
        body.stream,
        statusCode,
        headers: {'content-type': 'text/plain'},
      ),
    ),
  );

  test(
    'logs a request and a successful response once the body is read',
    () async {
      final response = await clientWith(200).send(
        http.Request('get', Uri.parse('https://example.com/items?page=2')),
      );
      body
        ..add([1, 2, 3])
        ..add([4, 5])
        ..close();
      expect(await response.stream.toBytes(), [1, 2, 3, 4, 5]);
      await flush();

      final request = argsOf('logNetworkRequest').single;
      expect(request['method'], 'GET');
      expect(request['host'], 'example.com');
      expect(request['path'], '/items');
      expect(request['query'], 'page=2');

      final logged = singleResponse();
      expect(logged['result'], 'success');
      expect(logged['statusCode'], 200);
      expect(logged['responseBodyBytesReceivedCount'], 5);
      expect(logged['headers'], {'content-type': 'text/plain'});
      expect(
        (logged['request'] as Map)['spanId'],
        request['spanId'],
        reason: 'request and response logs share a span',
      );
    },
  );

  test('logs a failed response for error status codes', () async {
    final response = await clientWith(
      404,
    ).send(http.Request('GET', Uri.parse('https://example.com/missing')));
    body.close();
    await response.stream.drain<void>();
    await flush();

    expect(singleResponse()['result'], 'failure');
    expect(singleResponse()['statusCode'], 404);
  });

  test('logs a failed response when the request cannot be sent', () async {
    final client = CaptureHttpClient(
      MockClient((_) => Future.error(const FormatException('offline'))),
    );

    await expectLater(
      client.get(Uri.parse('https://example.com')),
      throwsFormatException,
    );
    await flush();

    final logged = singleResponse();
    expect(logged['result'], 'failure');
    expect(logged['statusCode'], isNull);
    expect(logged['errorType'], 'FormatException');
  });

  test('logs a failed response when the body stream errors', () async {
    final response = await clientWith(
      200,
    ).send(http.Request('GET', Uri.parse('https://example.com')));
    body
      ..add([1, 2])
      ..addError(StateError('connection reset'))
      ..close();

    await expectLater(response.stream.toBytes(), throwsStateError);
    await flush();

    final logged = singleResponse();
    expect(logged['result'], 'failure');
    expect(logged['errorType'], 'StateError');
    expect(logged['responseBodyBytesReceivedCount'], 2);
  });

  test(
    'logs a canceled response with the bytes received when the body is canceled',
    () async {
      var upstreamCanceled = false;
      body = StreamController<List<int>>(
        onCancel: () => upstreamCanceled = true,
      );
      final response = await clientWith(
        200,
      ).send(http.Request('GET', Uri.parse('https://example.com')));
      body.add([1, 2, 3]);

      expect(await response.stream.first, [1, 2, 3]);
      await flush();

      expect(upstreamCanceled, isTrue);
      final logged = singleResponse();
      expect(logged['result'], 'canceled');
      expect(logged['statusCode'], 200);
      expect(logged['responseBodyBytesReceivedCount'], 3);
    },
  );

  test('forwards pause and resume to the inner response', () async {
    final response = await clientWith(
      200,
    ).send(http.Request('GET', Uri.parse('https://example.com')));
    final received = <List<int>>[];
    final subscription = response.stream.listen(received.add);

    subscription.pause();
    expect(body.isPaused, isTrue);
    subscription.resume();
    expect(body.isPaused, isFalse);

    body
      ..add([1])
      ..close();
    await subscription.asFuture<void>();
    await flush();

    expect(received, [
      [1],
    ]);
    expect(singleResponse()['result'], 'success');
  });

  test('logs a single response when an error is followed by cancel', () async {
    final response = await clientWith(
      200,
    ).send(http.Request('GET', Uri.parse('https://example.com')));
    final errors = <Object>[];
    final subscription = response.stream.listen(
      (_) {},
      onError: errors.add,
      cancelOnError: true,
    );
    body.addError(StateError('broken'));
    await flush();
    await subscription.cancel();
    await flush();

    expect(errors, hasLength(1));
    expect(singleResponse()['result'], 'failure');
  });
}
