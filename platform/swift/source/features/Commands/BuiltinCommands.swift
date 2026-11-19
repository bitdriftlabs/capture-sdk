// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

enum BuiltinCommands {
    static func make(screenshot: SessionReplayController?, memoryDump: MemoryDumpCommand) -> [CommandRegistry.Builtin] {
        let screenshotType = BuiltinCommandType.takeScreenshot
        let traceType = BuiltinCommandType.stackSamplingTrace
        return [
            makeBuiltin(screenshotType, artifactType: "screenshot") { arguments in
                guard arguments.isEmpty else {
                    return .failure(.invalidArguments(message: "screenshot does not accept arguments"))
                }
                guard let screenshot else {
                    return .failure(CommandError(message: "screenshot capture unavailable"))
                }
                return await screenshot.captureCommandScreenshot()
            },
            .init(type: traceType.rawValue, command: Command(key: traceType.key) { _ in
                .failure(CommandError(message: "not implemented"))
            }, artifactType: "stack_sampling_trace", timeoutMilliseconds: 15_000),
            memoryDump.builtin,
            makeBuiltin(.userSettingsClear, artifactType: "settings") {
                UserSettingsCommands.defaultsClear($0)
            },
            makeBuiltin(.userSettingsGet, artifactType: "settings") {
                UserSettingsCommands.defaultsGet($0)
            },
            makeBuiltin(.userSettingsRemove, artifactType: "settings") {
                UserSettingsCommands.defaultsRemove($0)
            },
            makeBuiltin(.userSettingsSet, artifactType: "settings") {
                UserSettingsCommands.defaultsSet($0)
            },
            makeBuiltin(.fileClear, artifactType: "file") {
                FileCommands.clear($0)
            },
            makeBuiltin(.fileDelete, artifactType: "file") {
                FileCommands.delete($0)
            },
            makeBuiltin(.fileList, artifactType: "file") {
                FileCommands.list($0)
            },
            makeBuiltin(.fileSize, artifactType: "file") {
                FileCommands.sizeKb($0)
            },
        ]
    }

    static func register(
        _ builtins: [CommandRegistry.Builtin],
        registry: CommandRegistry,
        logger: CoreLogging
    ) -> [String] {
        builtins.compactMap { builtin in
            let target = CommandsTarget(registry: registry, builtinType: builtin.type)
            return logger.registerBuiltinCommand(
                type: builtin.type,
                key: builtin.command.key,
                artifactType: builtin.artifactType,
                timeoutMilliseconds: builtin.timeoutMilliseconds,
                target: target
            ) ? nil : builtin.command.key
        }
    }
}

private func makeBuiltin(_ type: BuiltinCommandType, artifactType: String, handler: @escaping CommandHandler) -> CommandRegistry.Builtin {
    return .init(type: type.rawValue, command: Command(key: type.key, handler: handler), artifactType: artifactType)
}
