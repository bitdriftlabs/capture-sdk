// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct StackSnapshotCommand: Sendable {
    static let key = "ios_stack_snapshot"
    private let makeBridge: @Sendable () -> any StackSnapshotBridging
    private let execution: StackCaptureCommand

    init(
        fileManager: FileManager = .default,
        makeBridge: @escaping @Sendable () -> any StackSnapshotBridging = { StackCaptureBridge() }
    ) {
        self.makeBridge = makeBridge
        self.execution = StackCaptureCommand(fileManager: fileManager)
    }

    func make() -> Command {
        Command(key: Self.key) { arguments in
            guard arguments.count == 1,
                  let bufferSizeKb = StackCaptureCommand.positiveUInt32(arguments["buffer_size_kb"])
            else {
                return .failure(.invalidArguments(
                    message: "buffer_size_kb must be a positive integer representable as UInt32"
                ))
            }
            return await execution.run {
                let bridge = makeBridge()
                return StackCaptureOperation(bridge: bridge) { url in
                    bridge.capture(bufferSizeKb: bufferSizeKb, to: url).map { summary in
                        CommandResult(
                            attachment: .file(url: url, mimeType: "application/json"),
                            context: summary.context
                        )
                    }
                }
            }
        }
    }
}
