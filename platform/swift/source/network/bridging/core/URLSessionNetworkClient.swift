// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_implementationOnly import CapturePassable
import Dispatch
import Foundation

private let kDefaultPath = "/bitdrift_public.protobuf.client.v1.ApiService/Mux"

/// Client that supports sending gRPC traffic using `URLSession`.
final class URLSessionNetworkClient: NSObject {
    // A proxy `URLSession` delegate that redirects calls of interest to weakly held target delegate.
    // It helps us to avoid creating a retain cycle between `URLSessionNetworkClient` and `URLSession`
    // as `URLSession`'s delegate property is `strong` (retains) as opposed to `weak`.
    private final class ProxyURLSessionDelegate: NSObject, URLSessionDataDelegate, URLSessionTaskDelegate {
        fileprivate weak var delegate: (URLSessionDataDelegate & URLSessionTaskDelegate)?

        func urlSession(_ session: URLSession, task: URLSessionTask,
                        needNewBodyStream completionHandler: @escaping (InputStream?) -> Void)
        {
            self.delegate?.urlSession?(session, task: task, needNewBodyStream: completionHandler)
        }

        func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
            self.delegate?.urlSession?(session, dataTask: dataTask, didReceive: data)
        }

        func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64,
                        totalBytesSent: Int64, totalBytesExpectedToSend: Int64)
        {
            self.delegate?.urlSession?(
                session, task: task, didSendBodyData: bytesSent,
                totalBytesSent: totalBytesSent, totalBytesExpectedToSend: totalBytesExpectedToSend
            )
        }

        func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
            self.delegate?.urlSession?(session, task: task, didCompleteWithError: error)
        }

        func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge,
                        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?)
                            -> Void)
        {
            self.delegate?.urlSession?(session, didReceive: challenge, completionHandler: completionHandler)
        }
    }

    // Used to store network bandwidth metrics. Used for testing/benchmarking purposes only.
    // While it's not ideal that this code is included in prod builds its impact on the performance
    // of the SDK should be neglectable.
    struct Metrics: CustomDebugStringConvertible {
        var bytesReceivedCount: Int64
        var bytesSentCount: Int64

        init() {
            self.bytesReceivedCount = 0
            self.bytesSentCount = 0
        }

        mutating func record(bytesReceivedCount: Int64, bytesSentCount: Int64) {
            self.bytesReceivedCount += bytesReceivedCount
            self.bytesSentCount += bytesSentCount
        }

        var debugDescription: String {
            let formatter = ByteCountFormatter()
            let bytesReceived = formatter.string(fromByteCount: self.bytesReceivedCount)
            let bytesSent = formatter.string(fromByteCount: self.bytesSentCount)
            return "received: \(bytesReceived), sent: \(bytesSent)"
        }
    }

    private let apiBaseURL: URL
    private let timeout: TimeInterval
    // Used for benchmarking purposes only.
    private(set) var metrics = Metrics()

    private let proxyDelegate: ProxyURLSessionDelegate
    private let delegateQueue = OperationQueue.serial(
        withLabelSuffix: "URLSessionNetworkClient",
        target: .network
    )
    private let session: URLSession

    weak var logger: CoreLogging?

    init(apiBaseURL: URL, timeout: TimeInterval = kIdleTimeout) {
        self.apiBaseURL = apiBaseURL
        self.timeout = timeout
        self.proxyDelegate = ProxyURLSessionDelegate()

        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = self.timeout
        configuration.httpCookieStorage = nil
        self.session = URLSession(
            configuration: configuration,
            delegate: self.proxyDelegate,
            delegateQueue: self.delegateQueue
        )

        super.init()

        self.proxyDelegate.delegate = self
    }

    deinit {
        // TODO(Augustyniak): Fix shutdown cleanup logic.
        // Deinit is never called in production code and tests that actually use underlying Rust logger.
        self.session.invalidateAndCancel()
        self.taskConnectionMap.load().values.forEach { $0.value?.end() }
    }

    private let taskConnectionMap = Atomic<[Int: WeakBox<NetworkStreamConnection>]>([:])
}

extension URLSessionNetworkClient: Network {
    public func startStream(_ streamID: UInt, headers: [String: String]) -> NetworkStream {
        let connection = self.createConnection(
            to: self.apiBaseURL,
            handler: StreamHandle(streamID: streamID),
            headers: headers
        )
        connection.connect()

        return connection
    }
}

extension URLSessionNetworkClient: NetworkClient {
    func createConnection(to url: URL,
                          handler: ConnectionDataHandler,
                          headers: [String: String]) -> NetworkStreamConnection
    {
        let fullURL = url.appendingPathComponent(kDefaultPath)
        var request = URLRequest(url: fullURL)
        request.setInternalHeaders()
        request.httpMethod = "POST"
        for header in headers {
            request.addValue(header.value, forHTTPHeaderField: header.key)
        }

        let task = self.session.uploadTask(withStreamedRequest: request)
        self.logger?.log(
            level: .debug,
            message: "[Network] Creating a new connection to \(fullURL)",
            type: .internalsdk
        )

        let connection = URLSessionConnection(
            task: task,
            handler: handler,
            logger: self.logger
        )

        self.taskConnectionMap.update { $0[task.taskIdentifier] = WeakBox(connection) }

        return connection
    }

    // MARK: - Private

    private func connection(for task: URLSessionTask) -> NetworkStreamConnection? {
        guard let connection = self.taskConnectionMap.load()[task.taskIdentifier] else {
            self.logger?.log(
                level: .error,
                message: "[Network] Could not find handler for task: \(task.taskIdentifier)",
                type: .internalsdk
            )
            return nil
        }

        return connection.value
    }
}

extension URLSessionNetworkClient: URLSessionDataDelegate {
    func urlSession(_: URLSession, task: URLSessionTask,
                    needNewBodyStream completionHandler: @escaping (InputStream?) -> Void)
    {
        if let connection = self.connection(for: task) {
            // `openAndProvideStream` will internally check the state of the stream and terminate the if the
            // stream is in a bad state.
            connection.openAndProvideStream(completionHandler)
        } else {
            completionHandler(nil)
        }
    }

    func urlSession(_: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        self.metrics.record(bytesReceivedCount: Int64(data.count), bytesSentCount: 0)
        self.connection(for: dataTask)?.handler.onMessage(data)
    }

    func urlSession(_: URLSession, task _: URLSessionTask, didSendBodyData bytesSent: Int64,
                    totalBytesSent _: Int64,
                    totalBytesExpectedToSend _: Int64)
    {
        self.metrics.record(bytesReceivedCount: 0, bytesSentCount: bytesSent)
    }
}

extension URLSessionNetworkClient: URLSessionTaskDelegate {
    func urlSession(_: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        self.logger?.log(
            level: .error,
            message: "[Network] URLSession.didCompleteWithError: \(String(describing: error))",
            type: .internalsdk
        )
        self.connection(for: task)?.handler.onComplete(error)
    }

    func urlSession(_: URLSession, didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?)
                        -> Void)
    {
        guard
            challenge.protectionSpace.host == "localhost",
            let trust: SecTrust = challenge.protectionSpace.serverTrust
        else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // This codepath is only used for tests with the local test server.
        let credential = URLCredential(trust: trust)
        completionHandler(.useCredential, credential)
    }
}
