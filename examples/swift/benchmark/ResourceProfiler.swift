// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import CaptureTestBridge
import Foundation
import SwiftUI

private let kNetworkProfilingDuration = 5 * 60.0 // 5 minutes.
private let kPeriodicUpdatesTimeInterval = 10.0

final class ResourceProfiler {
    private var logger: Capture.Logger?
    private var loggerURL: URL?

    private var completionWorkItem: DispatchWorkItem?
    private var periodicUpdatesTimer: Timer?

    private let fileManager = FileManager.default

    private let appUsageSimulator = AppUsageSimulator()
    private var dateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .none
        formatter.timeStyle = .medium
        return formatter
    }()

    func startSimple(isRunning: Binding<Bool>) {
        self.startNetworkProfiling(
            isRunning: isRunning,
            name: "Simple",
            configuration: .init(sessionReplayConfiguration: nil),
            streamHandler: { streamID in
                configure_benchmarking_configuration(streamID)
                await_configuration_ack(streamID)
            }
        )
    }

    func startDefault(isRunning: Binding<Bool>) {
        self.startNetworkProfiling(
            isRunning: isRunning,
            name: "Default",
            configuration: .init(),
            streamHandler: { streamID in
                configure_benchmarking_configuration_with_workflows(streamID)
                await_configuration_ack(streamID)
            }
        )
    }

    func stop() {
        defer {
            self.logger = nil
        }

        self.completionWorkItem?.perform()
    }

    // MARK: - Private

    private func log(message: String) {
        print("[\(self.dateFormatter.string(from: Date()))] \(message)")
    }

    private func startNetworkProfiling(
        isRunning: Binding<Bool>,
        name: String,
        configuration: Configuration,
        streamHandler: (Int32) -> Void
    )
    {
        isRunning.wrappedValue = true

        // Ping every 60s
        let port = start_test_api_server(true, 60 * 1_000)

        let url = self.makeDirectoryURL()
        self.logger = Logger(
            withAPIKey: "foo",
            bufferDirectory: url,
            // swiftlint:disable:next force_unwrapping
            apiURL: URL(string: "https://localhost:\(port)")!,
            remoteErrorReporter: nil,
            configuration: configuration,
            sessionStrategy: .fixed(),
            dateProvider: nil,
            fieldProviders: [],
            enableNetwork: true,
            storageProvider: Storage.shared,
            timeProvider: SystemTimeProvider()
        )
        self.loggerURL = url

        let streamID = await_next_api_stream()
        await_api_server_received_handshake(streamID)
        streamHandler(streamID)

        let workItem = DispatchWorkItem { [weak self] in
            isRunning.wrappedValue.toggle()
            self?.complete(name: name)
        }

        self.completionWorkItem = workItem
        DispatchQueue.main.asyncAfter(
            deadline: DispatchTime.now() + kNetworkProfilingDuration,
            execute: workItem
        )

        self.appUsageSimulator.start()

        var passedTimeInterval = 0.0
        self.periodicUpdatesTimer = Timer.scheduledTimer(
            withTimeInterval: kPeriodicUpdatesTimeInterval,
            repeats: true
        ) { [weak self] _ in
            guard let self, let metrics = self.logger?.metrics else {
                return
            }

            passedTimeInterval += kPeriodicUpdatesTimeInterval
            let progress = Int((passedTimeInterval / kNetworkProfilingDuration * 100).rounded())
            if passedTimeInterval != kNetworkProfilingDuration {
                let storedSize = self.fileManager.directorySizeString(url: url)
                self
                    .log(
                        message: "\(progress)% (\(passedTimeInterval)s)... \(metrics), stored: \(storedSize)"
                    )
            }
        }

        self.log(
            message: "PROFILING STARTED - \"\(name)\" Configuration (\(kNetworkProfilingDuration)s)"
        )
    }

    private func complete(name: String) {
        defer {
            self.logger = nil
            self.loggerURL = nil
        }

        self.completionWorkItem?.cancel()
        self.completionWorkItem = nil
        self.periodicUpdatesTimer?.invalidate()
        self.appUsageSimulator.stop()

        guard let metrics = self.logger?.metrics else {
            return
        }

        let storedSize = self.loggerURL.flatMap { self.fileManager.directorySizeString(url: $0) } ?? "Unknown"

        self.log(message: """
        PROFILING STOPPED - \"\(name)\" Configuration (\(kNetworkProfilingDuration)s)
        ========================================================
            \(metrics), stored: \(storedSize)
        ========================================================
        """)
    }

    private func makeDirectoryURL() -> URL {
        let documentDirectories = NSSearchPathForDirectoriesInDomains(
            .documentDirectory,
            .userDomainMask,
            true
        )
        guard let documentDirectory = documentDirectories.first else {
            fatalError("document directories is empty")
        }
        return URL(fileURLWithPath: documentDirectory, isDirectory: true)
            .appendingPathComponent("sdk_dir_\(UUID().uuidString)")
    }
}

extension FileManager {
    private func totalDirectorySize(url: URL) throws -> Int {
        guard try url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory == true else {
            return 0
        }

        guard let urls = self.enumerator(at: url, includingPropertiesForKeys: nil)?.allObjects as? [URL] else
        {
            return 0
        }

        return try urls.lazy.reduce(0) {
            (try $1.resourceValues(forKeys: [.totalFileAllocatedSizeKey]).totalFileAllocatedSize ?? 0) + $0
        }
    }

    func directorySize(url: URL) -> Int {
        do {
            return try self.totalDirectorySize(url: url)
        } catch let error {
            print("failed to calculate directory size: \(error)")
            return -1
        }
    }

    func directorySizeString(url: URL) -> String {
        let size = self.directorySize(url: url)

        let bytesFormatter = ByteCountFormatter()
        return bytesFormatter.string(fromByteCount: Int64(size))
    }
}
