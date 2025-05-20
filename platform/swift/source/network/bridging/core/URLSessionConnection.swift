// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CapturePassable
import Foundation

final class URLSessionConnection: Connection {
    private let task: URLSessionUploadTask
    private weak var logger: CoreLogging?
    private var streams: (input: InputStream, output: OutputStream) = {
        var readStream: InputStream?
        var writeStream: OutputStream?

        Stream.getBoundStreams(withBufferSize: kMaxBufferSize, inputStream: &readStream,
                               outputStream: &writeStream)
        guard let readStream, let writeStream else {
            fatalError("[Network] Can't create the streams, did we run out of memory?")
        }

        return (readStream, writeStream)
    }()

    let handler: ConnectionDataHandler

    init(task: URLSessionUploadTask, handler: ConnectionDataHandler, logger: CoreLogging?) {
        self.task = task
        self.handler = handler
        self.logger = logger
    }

    deinit {
        self.end()
    }

    // MARK: - Connection

    /// Starts the connection
    func connect() {
        self.logger?.log(
            level: .debug,
            message: "[Network] Starting connections for task \(self.task.taskIdentifier)",
            type: .internalsdk
        )
        self.task.resume()
    }

    func openAndProvideStream(_ closure: (InputStream?) -> Void) {
        self.logger?.log(
            level: .debug,
            message: "[Network] Opening stream for task \(self.task.taskIdentifier)",
            type: .internalsdk
        )

        if self.streams.input.streamStatus == .notOpen, self.streams.output.streamStatus == .notOpen {
            self.streams.output.schedule(in: .main, forMode: .default)
            self.streams.output.open()

            closure(self.streams.input)
        } else {
            self.logger?.log(
                level: .warning,
                message: "[Network] Attempting to open stream that was already opened!",
                type: .internalsdk
            )
            self.end()
            closure(nil)
        }
    }

    /// Ends the connection and closes their associated streams. This can be called multiple times.
    func end() {
        self.logger?.log(
            level: .debug,
            message: "[Network] Ending connection for task \(self.task.taskIdentifier)",
            type: .internalsdk
        )
        self.task.cancel()
        self.streams.output.close()
    }
}

// MARK: - NetworkStream

extension URLSessionConnection: NetworkStream {
    /// Forwards data to the output stream. This API mirrors that of Foundation.Stream.write(to:)
    ///
    /// - parameter baseAddress: A data pointer.
    /// - parameter count:       A number of bytes to send.
    ///
    /// - returns: The number of bytes that were sent.
    func sendData(_ baseAddress: UnsafePointer<UInt8>, count: Int) -> Int {
        return self.streams.output.write(baseAddress, maxLength: count)
    }

    public func shutdown() {
        // This ends the connection, which in turn will lead to `URLSessionNetworkClient` receiving a
        // didComplete callback, which then gets forwarded to the handler. The handler will then close out the
        // stream on the Rust end. This all happens asynchronously.
        self.end()
    }
}
