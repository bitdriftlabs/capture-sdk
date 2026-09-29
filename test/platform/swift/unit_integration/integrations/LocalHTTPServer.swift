// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import Network

final class LocalHTTPServer: @unchecked Sendable {
    static let host = "127.0.0.1"

    static let shared = LocalHTTPServer()

    private static let responseBody = Data("ok".utf8)
    private static let headersTerminator = Data("\r\n\r\n".utf8)
    private static let chunkedTerminator = Data("0\r\n\r\n".utf8)
    private static let readChunkSize = 64 * 1024

    let port: UInt16
    private let listener: NWListener
    private let queue = DispatchQueue(label: "io.bitdrift.capture.tests.local-http-server")

    func url(path: String, query: String) -> URL {
        var components = URLComponents()
        components.scheme = "http"
        components.host = Self.host
        components.port = Int(self.port)
        components.path = path
        components.percentEncodedQuery = query
        // swiftlint:disable:next force_unwrapping
        return components.url!
    }

    private init() {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = NWEndpoint.hostPort(host: NWEndpoint.Host(Self.host), port: .any)

        guard let listener = try? NWListener(using: parameters) else {
            fatalError("[LocalHTTPServer] failed to create the listener")
        }
        self.listener = listener

        let ready = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { state in
            switch state {
            case .ready:
                ready.signal()
            case let .failed(error):
                fatalError("[LocalHTTPServer] listener failed: \(error)")
            default:
                break
            }
        }
        listener.newConnectionHandler = { [queue] connection in
            Self.serve(connection, on: queue)
        }
        listener.start(queue: self.queue)

        guard ready.wait(timeout: .now() + 5) == .success, let port = listener.port?.rawValue else {
            fatalError("[LocalHTTPServer] listener did not become ready")
        }
        self.port = port
    }

    // MARK: - Private

    private static func serve(_ connection: NWConnection, on queue: DispatchQueue) {
        connection.start(queue: queue)
        self.receiveRequest(on: connection, buffered: Data())
    }

    /// Reads until the whole request (headers and body) has arrived, then responds. Draining the body
    /// before responding matters: closing a socket with unread bytes resets the connection, and the
    /// client would then see an error instead of the response.
    ///
    /// - parameter connection: The accepted client connection.
    /// - parameter buffered:   The request bytes received so far.
    private static func receiveRequest(on connection: NWConnection, buffered: Data) {
        connection.receive(
            minimumIncompleteLength: 1,
            maximumLength: self.readChunkSize
        ) { data, _, isComplete, error in
            var buffered = buffered
            if let data {
                buffered.append(data)
            }

            if error != nil {
                connection.cancel()
                return
            }

            if self.isCompleteRequest(buffered) {
                self.respond(on: connection)
            } else if isComplete {
                connection.cancel()
            } else {
                self.receiveRequest(on: connection, buffered: buffered)
            }
        }
    }

    private static func isCompleteRequest(_ request: Data) -> Bool {
        guard let headersEnd = request.range(of: self.headersTerminator) else {
            return false
        }

        let headers = String(decoding: request[..<headersEnd.lowerBound], as: UTF8.self).lowercased()
        let body = request[headersEnd.upperBound...]

        if headers.contains("transfer-encoding: chunked") {
            // swiftlint:disable:next contains_over_range_nil_comparison
            return body.range(of: self.chunkedTerminator) != nil
        }

        return body.count >= self.contentLength(in: headers)
    }

    private static func contentLength(in headers: String) -> Int {
        for line in headers.split(separator: "\r\n") where line.hasPrefix("content-length:") {
            let value = line.dropFirst("content-length:".count).trimmingCharacters(in: .whitespaces)
            return Int(value) ?? 0
        }
        return 0
    }

    private static func respond(on connection: NWConnection) {
        var response = Data(
            (
                "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Content-Length: \(self.responseBody.count)\r\n"
                    + "Connection: close\r\n"
                    + "\r\n"
            ).utf8
        )
        response.append(self.responseBody)

        connection.send(content: response, isComplete: true, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }
}
