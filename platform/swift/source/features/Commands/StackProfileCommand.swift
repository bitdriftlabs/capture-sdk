// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

struct StackProfileCommand: Sendable {
    static let key = "ios_stack_profile"
    private let makeBridge: @Sendable () -> any StackProfileBridging
    private let execution: StackCaptureCommand

    init(
        fileManager: FileManager = .default,
        makeBridge: @escaping @Sendable () -> any StackProfileBridging = { StackCaptureBridge() }
    ) {
        self.makeBridge = makeBridge
        self.execution = StackCaptureCommand(fileManager: fileManager)
    }

    func make() -> Command {
        Command(key: Self.key) { arguments in
            guard arguments.count == 3,
                  let frequencyHz = StackCaptureCommand.positiveUInt32(arguments["frequency_hz"]),
                  frequencyHz <= 1_000_000_000,
                  let durationMs = StackCaptureCommand.positiveUInt32(arguments["duration_ms"]),
                  let bufferSizeKb = StackCaptureCommand.positiveUInt32(arguments["buffer_size_kb"])
            else {
                return .failure(.invalidArguments(
                    message: "Expected positive UInt32 values; frequency_hz must be at most 1e9"
                ))
            }
            return await execution.run {
                let bridge = makeBridge()
                return StackCaptureOperation(bridge: bridge) { url in
                    bridge.profile(
                        frequencyHz: frequencyHz, durationMs: durationMs,
                        bufferSizeKb: bufferSizeKb, to: url
                    )
                    .map { summary in
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
