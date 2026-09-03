// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import 'dart:math';

/// The outcome of an HTTP request.
enum HttpResult {
  /// The request completed successfully.
  success,

  /// The request failed.
  failure,

  /// The request was canceled before it completed.
  canceled,
}

/// A URL path plus an optional cardinality-collapsed template.
///
/// For example, [value] `/users/123` might pair with [template] `/users/<id>`
/// so requests to different user IDs group together on the backend.
class HttpUrlPath {
  final String value;
  final String? template;

  const HttpUrlPath(this.value, {this.template});

  Map<String, dynamic> toMap() => {
        'value': value,
        if (template != null) 'template': template,
      };
}

/// Performance metrics for an HTTP request/response cycle.
///
/// All fields are optional; duration fields are in milliseconds.
class HttpRequestMetrics {
  final int? requestBodyBytesSentCount;
  final int? responseBodyBytesReceivedCount;
  final int? requestHeadersBytesCount;
  final int? responseHeadersBytesCount;
  final int? dnsResolutionDurationMs;
  final int? tlsDurationMs;
  final int? tcpDurationMs;
  final int? fetchInitializationMs;
  final int? responseLatencyMs;
  final String? protocolName;

  const HttpRequestMetrics({
    this.requestBodyBytesSentCount,
    this.responseBodyBytesReceivedCount,
    this.requestHeadersBytesCount,
    this.responseHeadersBytesCount,
    this.dnsResolutionDurationMs,
    this.tlsDurationMs,
    this.tcpDurationMs,
    this.fetchInitializationMs,
    this.responseLatencyMs,
    this.protocolName,
  });

  Map<String, dynamic> toMap() => {
        if (requestBodyBytesSentCount != null)
          'requestBodyBytesSentCount': requestBodyBytesSentCount,
        if (responseBodyBytesReceivedCount != null)
          'responseBodyBytesReceivedCount': responseBodyBytesReceivedCount,
        if (requestHeadersBytesCount != null)
          'requestHeadersBytesCount': requestHeadersBytesCount,
        if (responseHeadersBytesCount != null)
          'responseHeadersBytesCount': responseHeadersBytesCount,
        if (dnsResolutionDurationMs != null)
          'dnsResolutionDurationMs': dnsResolutionDurationMs,
        if (tlsDurationMs != null) 'tlsDurationMs': tlsDurationMs,
        if (tcpDurationMs != null) 'tcpDurationMs': tcpDurationMs,
        if (fetchInitializationMs != null)
          'fetchInitializationMs': fetchInitializationMs,
        if (responseLatencyMs != null) 'responseLatencyMs': responseLatencyMs,
        if (protocolName != null) 'protocolName': protocolName,
      };
}

/// Describes an outgoing HTTP request.
///
/// Log with [Capture.logNetworkRequest] at the start of a network operation,
/// then pass the same instance (or one built with the same [spanId]) to
/// [Capture.logNetworkResponse] once the request completes, so the backend
/// can pair the two into a single network span.
class HttpRequestInfo {
  final String method;
  final String? host;
  final HttpUrlPath? path;
  final String? query;
  final Map<String, String>? headers;
  final int? bytesExpectedToSendCount;

  /// Identifies this request/response pair. Auto-generated if omitted.
  final String spanId;
  final Map<String, String>? extraFields;

  HttpRequestInfo({
    required this.method,
    this.host,
    this.path,
    this.query,
    this.headers,
    this.bytesExpectedToSendCount,
    String? spanId,
    this.extraFields,
  }) : spanId = spanId ?? _generateSpanId();

  Map<String, dynamic> toMap() => {
        'method': method,
        if (host != null) 'host': host,
        if (path != null) 'path': path!.toMap(),
        if (query != null) 'query': query,
        if (headers != null) 'headers': headers,
        if (bytesExpectedToSendCount != null)
          'bytesExpectedToSendCount': bytesExpectedToSendCount,
        'spanId': spanId,
        if (extraFields != null) 'extraFields': extraFields,
      };
}

/// Describes the outcome of an HTTP response.
class HttpResponse {
  final HttpResult result;
  final String? host;
  final HttpUrlPath? path;
  final String? query;
  final Map<String, String>? headers;
  final int? statusCode;

  /// A description of the error, if the request failed.
  ///
  /// There is no live exception/error object to bridge across platforms, so
  /// this is recorded as a plain string.
  final String? error;

  const HttpResponse({
    required this.result,
    this.host,
    this.path,
    this.query,
    this.headers,
    this.statusCode,
    this.error,
  });

  Map<String, dynamic> toMap() => {
        'result': result.name,
        if (host != null) 'host': host,
        if (path != null) 'path': path!.toMap(),
        if (query != null) 'query': query,
        if (headers != null) 'headers': headers,
        if (statusCode != null) 'statusCode': statusCode,
        if (error != null) 'error': error,
      };
}

/// Generates a random UUID v4 string without pulling in an external package.
String _generateSpanId() {
  final rand = Random.secure();
  final bytes = List<int>.generate(16, (_) => rand.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variant 10xx
  String hex(int start, int end) => bytes
      .sublist(start, end)
      .map((b) => b.toRadixString(16).padLeft(2, '0'))
      .join();
  return '${hex(0, 4)}-${hex(4, 6)}-${hex(6, 8)}-${hex(8, 10)}-${hex(10, 16)}';
}
