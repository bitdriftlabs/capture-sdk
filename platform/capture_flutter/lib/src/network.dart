import 'dart:math';

/// Outcome of a network request.
enum HttpResult { success, failure, canceled }

/// Information about an outgoing network request.
///
/// Log it with `Capture.logNetworkRequest` when the request starts and pass
/// it to [HttpResponseInfo] once it completes; both logs share [spanId].
class HttpRequestInfo {
  final String method;
  final String? host;
  final String? path;
  final String? pathTemplate;
  final String? query;
  final Map<String, String>? headers;
  final int? bytesExpectedToSendCount;
  final String spanId;
  final Map<String, String>? extraFields;
  final DateTime startedAt;

  HttpRequestInfo({
    required this.method,
    this.host,
    this.path,
    this.pathTemplate,
    this.query,
    this.headers,
    this.bytesExpectedToSendCount,
    String? spanId,
    this.extraFields,
  }) : spanId = spanId ?? _uuid(),
       startedAt = DateTime.now();

  /// Builds request info from a [uri], splitting it into host, path and query.
  factory HttpRequestInfo.fromUri(
    String method,
    Uri uri, {
    Map<String, String>? headers,
    int? bytesExpectedToSendCount,
    String? pathTemplate,
    Map<String, String>? extraFields,
  }) => HttpRequestInfo(
    method: method.toUpperCase(),
    host: uri.host.isEmpty ? null : uri.host,
    path: uri.path.isEmpty ? null : uri.path,
    pathTemplate: pathTemplate,
    query: uri.query.isEmpty ? null : uri.query,
    headers: headers,
    bytesExpectedToSendCount: bytesExpectedToSendCount,
    extraFields: extraFields,
  );

  Map<String, Object?> toMap() => {
    'method': method,
    'host': host,
    'path': path,
    'pathTemplate': pathTemplate,
    'query': query,
    'headers': headers,
    'bytesExpectedToSendCount': bytesExpectedToSendCount,
    'spanId': spanId,
    'extraFields': extraFields,
  };

  static final _random = Random.secure();

  static String _uuid() {
    final bytes = List<int>.generate(16, (_) => _random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
        '${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }
}

/// Information about the completion of a network request.
class HttpResponseInfo {
  final HttpRequestInfo request;
  final HttpResult result;
  final int? statusCode;
  final Map<String, String>? headers;
  final Object? error;
  final Duration duration;
  final int? requestBodyBytesSentCount;
  final int? responseBodyBytesReceivedCount;
  final Map<String, String>? extraFields;

  HttpResponseInfo({
    required this.request,
    required this.result,
    this.statusCode,
    this.headers,
    this.error,
    Duration? duration,
    this.requestBodyBytesSentCount,
    this.responseBodyBytesReceivedCount,
    this.extraFields,
  }) : duration = duration ?? DateTime.now().difference(request.startedAt);

  Map<String, Object?> toMap() => {
    'request': request.toMap(),
    'result': result.name,
    'statusCode': statusCode,
    'headers': headers,
    'errorType': error?.runtimeType.toString(),
    'errorMessage': error?.toString(),
    'durationMs': duration.inMilliseconds,
    'requestBodyBytesSentCount': requestBodyBytesSentCount,
    'responseBodyBytesReceivedCount': responseBodyBytesReceivedCount,
    'extraFields': extraFields,
  };
}
