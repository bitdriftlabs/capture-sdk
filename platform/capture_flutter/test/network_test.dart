import 'package:capture_flutter/capture_flutter.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('request info splits the uri', () {
    final request = HttpRequestInfo.fromUri(
      'post',
      Uri.parse('https://example.com/items/1?verbose=true'),
      pathTemplate: '/items/{id}',
      bytesExpectedToSendCount: 12,
    );

    expect(request.toMap(), {
      'method': 'POST',
      'host': 'example.com',
      'path': '/items/1',
      'pathTemplate': '/items/{id}',
      'query': 'verbose=true',
      'headers': null,
      'bytesExpectedToSendCount': 12,
      'spanId': request.spanId,
      'extraFields': null,
    });
  });

  test('each request gets a distinct v4 span id', () {
    final first = HttpRequestInfo(method: 'GET');
    final second = HttpRequestInfo(method: 'GET');

    expect(
      first.spanId,
      matches(
        RegExp(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
        ),
      ),
    );
    expect(first.spanId, isNot(second.spanId));
  });

  test('response duration defaults to the elapsed request time', () async {
    final request = HttpRequestInfo(method: 'GET');
    await Future<void>.delayed(const Duration(milliseconds: 20));

    final response = HttpResponseInfo(
      request: request,
      result: HttpResult.success,
    );

    expect(
      response.duration,
      greaterThanOrEqualTo(const Duration(milliseconds: 20)),
    );
    expect(response.duration, lessThan(const Duration(seconds: 10)));
    expect(response.toMap()['durationMs'], response.duration.inMilliseconds);
  });

  test('an explicit response duration is preserved', () {
    final response = HttpResponseInfo(
      request: HttpRequestInfo(method: 'GET'),
      result: HttpResult.canceled,
      duration: const Duration(milliseconds: 1234),
    );

    expect(response.duration, const Duration(milliseconds: 1234));
    expect(response.toMap()['durationMs'], 1234);
    expect(response.toMap()['result'], 'canceled');
  });

  test('response errors are reported by type and message', () {
    final map = HttpResponseInfo(
      request: HttpRequestInfo(method: 'GET'),
      result: HttpResult.failure,
      error: const FormatException('bad'),
    ).toMap();

    expect(map['errorType'], 'FormatException');
    expect(map['errorMessage'], 'FormatException: bad');
  });
}
