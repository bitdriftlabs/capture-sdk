// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

protocol StackCaptureCancelling: AnyObject, Sendable {
    func cancel()
}

struct StackCaptureOperation: Sendable {
    let bridge: any StackCaptureCancelling
    let capture: @Sendable (URL) -> Result<CommandResult, CommandError>
}

// FileManager is immutable here and supports concurrent file operations.
// File ownership and execution are shared by snapshots and profiles. Lock is held only while
// acquiring/releasing the request slot, never during native work, cancellation or awaiting results.
struct StackCaptureCommand: @unchecked Sendable {
    nonisolated(unsafe) private static let requestLock = Lock()
    nonisolated(unsafe) private static var isCapturing = false
    let fileManager: FileManager

    func run(
        makeOperation: @Sendable () -> StackCaptureOperation
    ) async -> Result<CommandResult, CommandError> {
        guard !Task.isCancelled else {
            return .failure(Self.cancelled)
        }
        guard Self.acquire() else {
            return .failure(.alreadyExecuting)
        }
        defer { Self.release() }
        let operation = makeOperation()
        let url = fileManager.temporaryDirectory
            .appendingPathComponent("bitdrift-stack-\(UUID().uuidString).json")
        let result = await withTaskCancellationHandler {
            await Task.detached(priority: .utility) {
                operation.capture(url)
            }.value
        } onCancel: {
            operation.bridge.cancel()
        }
        if Task.isCancelled {
            if case let .success(result) = result {
                result.attachment?.discardOwnedFile(using: fileManager)
            }
            return .failure(Self.cancelled)
        }
        return result
    }

    static func positiveUInt32(_ argument: CommandArgument?) -> UInt32? {
        switch argument {
        case let .uint64(value) where value > 0:
            return UInt32(exactly: value)
        case let .int64(value) where value > 0:
            return UInt32(exactly: value)
        default:
            return nil
        }
    }

    private static let cancelled = CommandError(message: "cancelled")

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
