// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation

enum StackSnapshotCommand {
    static let key = "ios_stack_snapshot"

    static func make() -> Command {
        Command(key: key) { arguments in
            guard arguments.count == 1,
                  let bufferSizeKb = bufferSize(arguments["bufferSizeKb"])
            else {
                return .failure(.invalidArguments(description: "bufferSizeKb must be an integer from 16 through 16384 KiB"))
            }
            guard !Task.isCancelled else {
                return .failure(CommandError(title: "Command cancelled"))
            }
            let result = await Task.detached(priority: .utility) {
                let url = FileManager.default.temporaryDirectory
                    .appendingPathComponent("bitdrift-stack-\(UUID().uuidString).json")
                let status = url.path.withCString { capture_stack_snapshot(bufferSizeKb, $0) }
                guard status == 0 else {
                    return Result<CommandResult, CommandError>.failure(error(status))
                }
                return .success(CommandResult(
                    attachment: .file(url: url, mimeType: "application/json"),
                    context: ["capture": "snapshot", "unwinder": "arm64_frame_pointer"]
                ))
            }.value
            if Task.isCancelled {
                if case let .success(result) = result {
                    result.attachment?.discardOwnedFile()
                }
                return .failure(CommandError(title: "Command cancelled"))
            }
            return result
        }
    }

    private static func bufferSize(_ argument: CommandArgument?) -> UInt32? {
        let value: UInt64
        switch argument {
        case let .uint64(unsigned): value = unsigned
        case let .int64(signed) where signed > 0: value = UInt64(signed)
        default: return nil
        }
        guard (16 ... 16384).contains(value) else { return nil }
        return UInt32(value)
    }

    private static func error(_ status: Int32) -> CommandError {
        switch status {
        case 1: CommandError.invalidArguments(description: "invalid capture buffer")
        case 2: CommandError.alreadyExecuting
        case 3: CommandError(title: "stack snapshot requires ARM64")
        case 4: CommandError(title: "stack capture failed")
        default: CommandError(title: "stack snapshot attachment could not be written")
        }
    }
}
