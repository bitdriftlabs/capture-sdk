import Capture
import SwiftyBeaver

extension Integration {
    /// A Capture SDK integration that forwards all logs emitted using the `SwiftyBeaver`
    /// logging framework to Capture SDK.
    ///
    /// - returns: The `SwiftyBeaver` Capture Logger SDK integration.
    public static func swiftyBeaver() -> Integration {
        return Integration { logger, _ in
            SwiftyBeaver.addDestination(
                CaptureSwiftyBeaverLogger(logger: logger)
            )
        }
    }
}

/// The wrapper around Capture SDK logger that conforms to `BaseDestination` protocol from `SwiftyBeaver`
/// library and can be used as a drop-in solution for forwarding `SwiftyBeaver` logs to bitdrift
/// Capture SDK.
final class CaptureSwiftyBeaverLogger: BaseDestination {
    private let logger: Logging

    init(logger: Logging) {
        self.logger = logger
        super.init()
    }

    override func send(
        _ level: SwiftyBeaver.Level,
        msg: String,
        thread: String,
        file: String,
        function: String,
        line: Int,
        context: Any? = nil
    ) -> String? {
        let fields = context.flatMap { context in
            // swiftlint:disable line_length
            // Following formatting logic from
            // https://github.com/SwiftyBeaver/SwiftyBeaver/blob/d60a21a3878c487db07ec1e2df697fa24839918b/Sources/BaseDestination.swift#L239-L240
            return [
                "context": String(describing: context).trimmingCharacters(in: .whitespacesAndNewlines),
                "source": "SwiftBeaver",
                "thread": thread,
            ]
        }

        self.logger.log(
            level: LogLevel(level),
            message: msg,
            file: file,
            line: line,
            function: function,
            fields: fields,
            error: nil
        )

        return super.send(
            level,
            msg: msg,
            thread: thread,
            file: file,
            function: function,
            line: line,
            context: context
        )
    }
}

extension Capture.LogLevel {
    /// Initializes a new instance of Capture log level using provided `SwiftyBeaver` log level.
    ///
    /// - parameter logLevel: `SwiftyBeaver` log level.
    init(_ logLevel: SwiftyBeaver.Level) {
        switch logLevel {
        case .verbose:
            self = .trace
        case .debug:
            self = .debug
        case .info:
            self = .info
        case .warning:
            self = .warning
        case .error:
            self = .error
        case .critical:
            self = .error
        case .fault:
            self = .error
        }
    }
}
