// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
import Foundation
import Network

/// Frequently captures the app screen and sends the binary array over a persistent websocket connection.
final class ReplayPreviewClient {
    /// The underlying `Replay` instance held by the WebSocket client
    let replay = Replay()

    private var timer: Timer?
    private var content: Data?
    private var lastSent: Data?
    private var task: URLSessionWebSocketTask?
    private let url: URL

    init(hostname: String, port: Int) {
        // swiftlint:disable:next force_unwrapping
        self.url = URL(string: "ws://\(hostname):\(port)/echo")!
    }

    /// Starts a timer that captures the app's screen with the given interval and connects the persistent
    /// websocket.
    ///
    /// - parameter interval: The frequency of the timer (seconds)
    func start(withInterval interval: TimeInterval = 0.5) {
        self.connect()
        self.timer?.invalidate()
        self.timer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            guard let self else {
                return
            }

            DispatchQueue.main.async {
                let content = self.replay.capture()
                self.content = content
                self.send(data: content)
            }
        }
    }

    /// Stops the timer if it's running and disconnect the websocket.
    func stop() {
        self.task?.cancel()
        self.timer?.invalidate()
        self.timer = nil
    }

    // MARK: - Private methods

    private func connect() {
        self.task?.cancel()

        let session = URLSession(configuration: .default)
        self.task = session.webSocketTask(with: self.url)
        self.task?.resume()

        if let content = self.content {
            self.send(data: content)
        }
    }

    private func send(data: Data) {
        guard data != self.lastSent else {
            return
        }

        self.task?.send(.data(data)) { [weak self] error in
            if error != nil {
                self?.connect()
                return
            }

            self?.lastSent = data
        }
    }
}
