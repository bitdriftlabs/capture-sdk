import 'dart:async';

import 'package:http/http.dart' as http;

import 'capture.dart';
import 'network.dart';

/// A `package:http` client that logs every request and response to Capture.
///
/// ```dart
/// final client = CaptureHttpClient();
/// final response = await client.get(Uri.parse('https://example.com'));
/// ```
class CaptureHttpClient extends http.BaseClient {
  CaptureHttpClient([http.Client? inner]) : _inner = inner ?? http.Client();

  final http.Client _inner;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final requestInfo = HttpRequestInfo.fromUri(
      request.method,
      request.url,
      headers: request.headers,
      bytesExpectedToSendCount: request.contentLength,
    );
    Capture.logNetworkRequest(requestInfo);

    final http.StreamedResponse response;
    try {
      response = await _inner.send(request);
    } catch (error) {
      Capture.logNetworkResponse(
        HttpResponseInfo(
          request: requestInfo,
          result: HttpResult.failure,
          error: error,
        ),
      );
      rethrow;
    }

    var received = 0;
    var logged = false;
    void logResponse(HttpResult result, {Object? error}) {
      if (logged) return;
      logged = true;
      Capture.logNetworkResponse(
        HttpResponseInfo(
          request: requestInfo,
          result: result,
          statusCode: response.statusCode,
          headers: response.headers,
          error: error,
          requestBodyBytesSentCount: request.contentLength,
          responseBodyBytesReceivedCount: received,
        ),
      );
    }

    late final StreamSubscription<List<int>> subscription;
    late final StreamController<List<int>> controller;
    controller = StreamController<List<int>>(
      sync: true,
      onListen: () {
        subscription = response.stream.listen(
          (chunk) {
            received += chunk.length;
            controller.add(chunk);
          },
          onError: (Object error, StackTrace stack) {
            logResponse(HttpResult.failure, error: error);
            controller.addError(error, stack);
          },
          onDone: () {
            logResponse(
              response.statusCode < 400
                  ? HttpResult.success
                  : HttpResult.failure,
            );
            controller.close();
          },
        );
      },
      onPause: () => subscription.pause(),
      onResume: () => subscription.resume(),
      onCancel: () {
        logResponse(HttpResult.canceled);
        return subscription.cancel();
      },
    );

    return http.StreamedResponse(
      http.ByteStream(controller.stream),
      response.statusCode,
      contentLength: response.contentLength,
      request: response.request,
      headers: response.headers,
      isRedirect: response.isRedirect,
      persistentConnection: response.persistentConnection,
      reasonPhrase: response.reasonPhrase,
    );
  }

  @override
  void close() => _inner.close();
}
