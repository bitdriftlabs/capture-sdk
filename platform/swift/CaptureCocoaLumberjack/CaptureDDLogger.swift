import Capture
#if SWIFT_PACKAGE
import CocoaLumberjackSwift
#else
import CocoaLumberjack
#endif
import os

extension Integration {
    /// A Capture SDK integration that forwards all logs emitted using the `CocoaLumberjack`
    /// logging framework to Capture SDK.
    ///
    /// - returns: The `CocoaLumberjack` Capture Logger SDK integration.
    public static func cocoaLumberjack() -> Integration {
        return Integration { logger, _ in
            DDLog.add(CaptureDDLogger(logger: logger))
        }
    }
}

/// The wrapper around Capture SDK logger that conforms to `DDLogger` protocol from `CocoaLumberjack`
/// library and can be used as a drop-in solution for forwarding `CocoaLumberjack` logs to bitdrift
/// Capture SDK.
final class CaptureDDLogger: NSObject, DDLogger {
    private let logger: Logging

    var logFormatter: DDLogFormatter?

    init(logger: Logging) {
        self.logger = logger
        super.init()
    }

    func log(message logMessage: DDLogMessage) {
        guard let level = LogLevel(logMessage.level) else {
            return
        }

        self.logger.log(
            level: level,
            message: logMessage.message,
            file: logMessage.file,
            line: Int(logMessage.line),
            function: logMessage.function,
            fields: [
                "source": "CocoaLumberjack",
                "thread": logMessage.threadID,
            ],
            error: nil
        )
    }
}

extension LogLevel {
    /// Initializes a new instance of Capture log level using provided Cocoa Lumberjack log level.
    ///
    /// - parameter logLevel: Cocoa Lumberjack log level.
    public init?(_ logLevel: DDLogLevel) {
        switch logLevel {
        case .off:
            return nil
        case .error:
            self = .error
        case .warning:
            self = .warning
        case .info:
            self = .info
        case .debug:
            self = .debug
        case .verbose, .all:
            self = .trace
        default:
            self = .debug
        }
    }
}
