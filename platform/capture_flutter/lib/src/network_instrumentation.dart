// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'capture.dart';
import 'network.dart';

/// Installs automatic network instrumentation. See
/// [Capture.enableNetworkInstrumentation] (the public entry point) for
/// coverage and caveats.
void installCaptureNetworkInstrumentation() {
  HttpOverrides.global = _CaptureHttpOverrides(HttpOverrides.current);
}

class _CaptureHttpOverrides extends HttpOverrides {
  final HttpOverrides? _previous;
  _CaptureHttpOverrides(this._previous);

  @override
  HttpClient createHttpClient(SecurityContext? context) {
    final previous = _previous;
    final inner =
        previous != null ? previous.createHttpClient(context) : super.createHttpClient(context);
    return _InstrumentedHttpClient(inner);
  }

  @override
  String findProxyFromEnvironment(Uri url, Map<String, String>? environment) {
    final previous = _previous;
    if (previous != null) return previous.findProxyFromEnvironment(url, environment);
    return super.findProxyFromEnvironment(url, environment);
  }
}

class _InstrumentedHttpClient implements HttpClient {
  final HttpClient _inner;
  _InstrumentedHttpClient(this._inner);

  @override
  Future<HttpClientRequest> open(String method, String host, int port, String path) async {
    return _InstrumentedHttpClientRequest(await _inner.open(method, host, port, path));
  }

  @override
  Future<HttpClientRequest> openUrl(String method, Uri url) async {
    return _InstrumentedHttpClientRequest(await _inner.openUrl(method, url));
  }

  @override
  Future<HttpClientRequest> get(String host, int port, String path) => open('GET', host, port, path);
  @override
  Future<HttpClientRequest> getUrl(Uri url) => openUrl('GET', url);
  @override
  Future<HttpClientRequest> post(String host, int port, String path) =>
      open('POST', host, port, path);
  @override
  Future<HttpClientRequest> postUrl(Uri url) => openUrl('POST', url);
  @override
  Future<HttpClientRequest> put(String host, int port, String path) => open('PUT', host, port, path);
  @override
  Future<HttpClientRequest> putUrl(Uri url) => openUrl('PUT', url);
  @override
  Future<HttpClientRequest> patch(String host, int port, String path) =>
      open('PATCH', host, port, path);
  @override
  Future<HttpClientRequest> patchUrl(Uri url) => openUrl('PATCH', url);
  @override
  Future<HttpClientRequest> delete(String host, int port, String path) =>
      open('DELETE', host, port, path);
  @override
  Future<HttpClientRequest> deleteUrl(Uri url) => openUrl('DELETE', url);
  @override
  Future<HttpClientRequest> head(String host, int port, String path) =>
      open('HEAD', host, port, path);
  @override
  Future<HttpClientRequest> headUrl(Uri url) => openUrl('HEAD', url);

  @override
  Duration get idleTimeout => _inner.idleTimeout;
  @override
  set idleTimeout(Duration value) => _inner.idleTimeout = value;

  @override
  Duration? get connectionTimeout => _inner.connectionTimeout;
  @override
  set connectionTimeout(Duration? value) => _inner.connectionTimeout = value;

  @override
  int? get maxConnectionsPerHost => _inner.maxConnectionsPerHost;
  @override
  set maxConnectionsPerHost(int? value) => _inner.maxConnectionsPerHost = value;

  @override
  bool get autoUncompress => _inner.autoUncompress;
  @override
  set autoUncompress(bool value) => _inner.autoUncompress = value;

  @override
  String? get userAgent => _inner.userAgent;
  @override
  set userAgent(String? value) => _inner.userAgent = value;

  @override
  set authenticate(Future<bool> Function(Uri url, String scheme, String? realm)? f) =>
      _inner.authenticate = f;

  @override
  set authenticateProxy(
    Future<bool> Function(String host, int port, String scheme, String? realm)? f,
  ) =>
      _inner.authenticateProxy = f;

  @override
  set badCertificateCallback(
    bool Function(X509Certificate cert, String host, int port)? callback,
  ) =>
      _inner.badCertificateCallback = callback;

  @override
  set findProxy(String Function(Uri url)? f) => _inner.findProxy = f;

  @override
  set connectionFactory(
    Future<ConnectionTask<Socket>> Function(Uri url, String? proxyHost, int? proxyPort)? f,
  ) =>
      _inner.connectionFactory = f;

  @override
  set keyLog(Function(String line)? callback) => _inner.keyLog = callback;

  @override
  void addCredentials(Uri url, String realm, HttpClientCredentials credentials) =>
      _inner.addCredentials(url, realm, credentials);

  @override
  void addProxyCredentials(String host, int port, String realm, HttpClientCredentials credentials) =>
      _inner.addProxyCredentials(host, port, realm, credentials);

  @override
  void close({bool force = false}) => _inner.close(force: force);
}

/// The header apps already use (see the shop demos' `x-capture-path-template`
/// convention) to hint a cardinality-collapsed path template, e.g.
/// `/users/<id>`. Automatic instrumentation has no way to infer this on its
/// own, unlike a caller of the manual API who can supply [HttpUrlPath.template]
/// directly.
const _pathTemplateHeader = 'x-capture-path-template';

class _InstrumentedHttpClientRequest implements HttpClientRequest {
  final HttpClientRequest _inner;
  _InstrumentedHttpClientRequest(this._inner);

  int _bytesWritten = 0;
  bool _requestLogged = false;
  HttpRequestInfo? _requestInfo;
  DateTime? _startedAt;
  Future<HttpClientResponse>? _doneFuture;

  Future<void> _logRequestIfNeeded() async {
    if (_requestLogged) return;
    _requestLogged = true;
    _startedAt = DateTime.now();

    final uri = _inner.uri;
    final template = _inner.headers.value(_pathTemplateHeader);
    final headers = <String, String>{};
    _inner.headers.forEach((name, values) => headers[name] = values.join(', '));

    final info = HttpRequestInfo(
      method: _inner.method,
      host: uri.host.isEmpty ? null : uri.host,
      path: HttpUrlPath(uri.path.isEmpty ? '/' : uri.path, template: template),
      query: uri.query.isEmpty ? null : uri.query,
      headers: headers.isEmpty ? null : headers,
      bytesExpectedToSendCount: _inner.contentLength >= 0 ? _inner.contentLength : null,
    );
    _requestInfo = info;
    await Capture.logNetworkRequest(info);
  }

  @override
  Future<HttpClientResponse> close() => _doneFuture ??= _close();

  Future<HttpClientResponse> _close() async {
    await _logRequestIfNeeded();
    final start = _startedAt!;
    final request = _requestInfo!;
    try {
      final response = await _inner.close();
      return _InstrumentedHttpClientResponse(response, request, start);
    } catch (e) {
      final durationMs = DateTime.now().difference(start).inMilliseconds;
      unawaited(Capture.logNetworkResponse(
        request,
        HttpResponse(result: HttpResult.failure, error: '$e'),
        durationMs: durationMs,
        metrics: HttpRequestMetrics(requestBodyBytesSentCount: _bytesWritten),
      ));
      rethrow;
    }
  }

  @override
  Future<HttpClientResponse> get done => close();

  // -- IOSink: routed through add() for best-effort request-body byte counts.

  @override
  Encoding encoding = utf8;

  @override
  void add(List<int> data) {
    _bytesWritten += data.length;
    _inner.add(data);
  }

  @override
  void write(Object? object) {
    final text = object == null ? '' : '$object';
    if (text.isEmpty) return;
    add((_inner.encoding).encode(text));
  }

  @override
  void writeAll(Iterable objects, [String separator = '']) {
    var first = true;
    for (final obj in objects) {
      if (!first && separator.isNotEmpty) write(separator);
      write(obj);
      first = false;
    }
  }

  @override
  void writeln([Object? object = '']) {
    write(object);
    write('\n');
  }

  @override
  void writeCharCode(int charCode) => write(String.fromCharCode(charCode));

  @override
  void addError(Object error, [StackTrace? stackTrace]) => _inner.addError(error, stackTrace);

  @override
  Future addStream(Stream<List<int>> stream) {
    return _inner.addStream(stream.map((data) {
      _bytesWritten += data.length;
      return data;
    }));
  }

  @override
  Future flush() => _inner.flush();

  // -- HttpClientRequest passthroughs.

  @override
  bool get persistentConnection => _inner.persistentConnection;
  @override
  set persistentConnection(bool value) => _inner.persistentConnection = value;

  @override
  bool get followRedirects => _inner.followRedirects;
  @override
  set followRedirects(bool value) => _inner.followRedirects = value;

  @override
  int get maxRedirects => _inner.maxRedirects;
  @override
  set maxRedirects(int value) => _inner.maxRedirects = value;

  @override
  String get method => _inner.method;

  @override
  Uri get uri => _inner.uri;

  @override
  int get contentLength => _inner.contentLength;
  @override
  set contentLength(int value) => _inner.contentLength = value;

  @override
  bool get bufferOutput => _inner.bufferOutput;
  @override
  set bufferOutput(bool value) => _inner.bufferOutput = value;

  @override
  HttpHeaders get headers => _inner.headers;

  @override
  List<Cookie> get cookies => _inner.cookies;

  @override
  HttpConnectionInfo? get connectionInfo => _inner.connectionInfo;

  @override
  void abort([Object? exception, StackTrace? stackTrace]) => _inner.abort(exception, stackTrace);
}

class _InstrumentedHttpClientResponse extends Stream<List<int>> implements HttpClientResponse {
  final HttpClientResponse _inner;
  final HttpRequestInfo _request;
  final DateTime _start;
  _InstrumentedHttpClientResponse(this._inner, this._request, this._start);

  bool _resultLogged = false;
  int _bytesReceived = 0;

  @override
  StreamSubscription<List<int>> listen(
    void Function(List<int> event)? onData, {
    Function? onError,
    void Function()? onDone,
    bool? cancelOnError,
  }) {
    return _inner.listen(
      (data) {
        _bytesReceived += data.length;
        onData?.call(data);
      },
      onError: (Object e, StackTrace st) {
        _logResponse(failed: true, error: '$e');
        final handler = onError;
        if (handler is void Function(Object, StackTrace)) {
          handler(e, st);
        } else if (handler is void Function(Object)) {
          handler(e);
        }
      },
      onDone: () {
        _logResponse(failed: false);
        onDone?.call();
      },
      cancelOnError: cancelOnError,
    );
  }

  void _logResponse({required bool failed, String? error}) {
    if (_resultLogged) return;
    _resultLogged = true;
    final durationMs = DateTime.now().difference(_start).inMilliseconds;
    unawaited(Capture.logNetworkResponse(
      _request,
      HttpResponse(
        result: failed ? HttpResult.failure : HttpResult.success,
        statusCode: _inner.statusCode,
        error: error,
      ),
      durationMs: durationMs,
      metrics: HttpRequestMetrics(responseBodyBytesReceivedCount: _bytesReceived),
    ));
  }

  @override
  int get statusCode => _inner.statusCode;
  @override
  String get reasonPhrase => _inner.reasonPhrase;
  @override
  int get contentLength => _inner.contentLength;
  @override
  HttpClientResponseCompressionState get compressionState => _inner.compressionState;
  @override
  bool get persistentConnection => _inner.persistentConnection;
  @override
  bool get isRedirect => _inner.isRedirect;
  @override
  List<RedirectInfo> get redirects => _inner.redirects;
  @override
  Future<HttpClientResponse> redirect([String? method, Uri? url, bool? followLoops]) =>
      _inner.redirect(method, url, followLoops);
  @override
  HttpHeaders get headers => _inner.headers;
  @override
  Future<Socket> detachSocket() => _inner.detachSocket();
  @override
  List<Cookie> get cookies => _inner.cookies;
  @override
  X509Certificate? get certificate => _inner.certificate;
  @override
  HttpConnectionInfo? get connectionInfo => _inner.connectionInfo;
}
