enum BuiltinCommands {
    static func make(screenshot: SessionReplayController?) -> [CommandRegistry.Builtin] {
        let screenshotType = BuiltinCommandType.takeScreenshot
        let traceType = BuiltinCommandType.stackSamplingTrace
        return [
            .init(type: screenshotType.rawValue, command: Command(key: screenshotType.key) { arguments in
                guard arguments.isEmpty else {
                    return .failure(.invalidArguments(description: "screenshot does not accept arguments"))
                }
                guard let screenshot else {
                    return .failure(CommandError(title: "screenshot capture unavailable"))
                }
                return await screenshot.captureCommandScreenshot()
            }, artifactType: "screenshot"),
            .init(type: traceType.rawValue, command: Command(key: traceType.key) { _ in
                .failure(CommandError(title: "not implemented"))
            }, artifactType: "stack_sampling_trace", timeoutMilliseconds: 15_000),
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
