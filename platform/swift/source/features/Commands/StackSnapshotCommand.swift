// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct StackSnapshotCommand: Sendable {
    static let key = "ios_stack_snapshot"
    // Shared across instances; both accesses to isCapturing are protected by requestLock.
    nonisolated(unsafe) private static let requestLock = Lock()
    nonisolated(unsafe) private static var isCapturing = false

    private let makeBridge: @Sendable () -> any StackSnapshotBridging
    private let files: StackSnapshotFiles

    init(
        fileManager: FileManager = .default,
        makeBridge: @escaping @Sendable () -> any StackSnapshotBridging = { StackSnapshotBridge() }
    ) {
        self.makeBridge = makeBridge
        self.files = StackSnapshotFiles(fileManager: fileManager)
    }

    func make() -> Command {
        Command(key: Self.key) { arguments in
            guard arguments.count == 1,
                  let bufferSizeKb = bufferSize(arguments["buffer_size_kb"])
            else {
                return .failure(.invalidArguments(
                    description: "buffer_size_kb must be a positive integer representable as UInt32"
                ))
            }
            guard !Task.isCancelled else {
                return .failure(Self.cancelled)
            }
            guard Self.acquire() else {
                return .failure(.alreadyExecuting)
            }
            // Keep the slot through worker completion and late-attachment cleanup. Cancellation
            // only signals the bridge; it cannot release the slot while the worker is still active.
            defer { Self.release() }
            let bridge = makeBridge()
            let result = await withTaskCancellationHandler {
                await Task.detached(priority: .utility) {
                    capture(bufferSizeKb, using: bridge)
                }.value
            } onCancel: {
                bridge.cancel()
            }
            if Task.isCancelled {
                if case let .success(result) = result { files.discard(result.attachment) }
                return .failure(Self.cancelled)
            }
            return result
        }
    }

    private static let cancelled = CommandError(title: "cancelled")

    private func capture(
        _ bufferSizeKb: UInt32,
        using bridge: any StackSnapshotBridging
    ) -> Result<CommandResult, CommandError> {
        let url = files.makeURL()
        return bridge.capture(bufferSizeKb: bufferSizeKb, to: url).map { summary in
            CommandResult(
                attachment: .file(url: url, mimeType: "application/json"),
                context: summary.context
            )
        }
    }

    private func bufferSize(_ argument: CommandArgument?) -> UInt32? {
        let value: UInt64
        switch argument {
        case let .uint64(unsigned):
            value = unsigned
        case let .int64(signed) where signed > 0:
            value = UInt64(signed)
        default:
            return nil
        }
        guard value > 0 else {
            return nil
        }
        return UInt32(exactly: value)
    }

    private static func acquire() -> Bool {
        requestLock.withLock {
            guard !isCapturing else {
                return false
            }
            isCapturing = true
            return true
        }
    }

    private static func release() {
        requestLock.withLock { isCapturing = false }
    }
}

// FileManager supports concurrent file operations. This wrapper never changes its delegate or
// configuration, and stores no mutable request state.
private struct StackSnapshotFiles: @unchecked Sendable {
    let fileManager: FileManager

    func makeURL() -> URL {
        fileManager.temporaryDirectory.appendingPathComponent("bitdrift-stack-\(UUID().uuidString).json")
    }

    func discard(_ attachment: CommandAttachment?) {
        attachment?.discardOwnedFile(using: fileManager)
    }
}
