// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

public enum SpanResult {
    /// The operation completed succeeded.
    case success
    /// The operation failed.
    case failure
    /// The operation was canceled.
    case canceled
    /// The result of the operation is unknown.
    case unknown
}

/// Represents a single operation that is started and can be ended. The SDK emits two logs for each span:
/// one when the span is started and another when the `Span.end()` method is called. If the `Span.end()`
/// method is not called and the `Span` is deinitialized, the span ends with an `unknown` result.
public final class Span {
    enum EventType: String {
        case start = "start"
        case end = "end"
    }

    /// This is the autogenerated unique identifier of the span
    public let id = UUID()
    /// The human-readable name of the span, useful to group same-kind spans together when shown in the UI
    public let name: String
    /// The ID of the parent span, Spans with nil `parentSpanID` are considered root spans
    public let parentSpanID: UUID?

    private let timeProvider: TimeProvider
    private let startedAt: Uptime
    private let customStartTimeInterval: TimeInterval?

    // We keep a weak instance around to avoid retain cycles. In practice, it doesn't matter as the SDK
    // creates and manages only one `Logger` instance.
    private let logger: Atomic<WeakCoreLogging?>

    private let logLevel: LogLevel
    private let fields: Fields?

    private let file: String?
    private let line: Int?
    private let function: String?

    init(
        logger: CoreLogging, name: String, level: LogLevel, file: String?, line: Int?,
        function: String?, fields: Fields?, timeProvider: TimeProvider,
        customStartTimeInterval: TimeInterval?, parentSpanID: UUID?, emitStartEvent: Bool
    ) {
        self.timeProvider = timeProvider
        self.startedAt = timeProvider.uptime()
        self.customStartTimeInterval = customStartTimeInterval

        self.logger = Atomic(WeakCoreLogging(logger))
        self.logLevel = level
        self.name = name
        self.fields = fields
        self.parentSpanID = parentSpanID

        self.file = file
        self.line = line
        self.function = function

        if emitStartEvent {
            self.logger.load()?.underlyingLogger?.log(
                level: level,
                message: "",
                file: file,
                line: line,
                function: function,
                fields: self.makeStartEventFields().mergedOmittingConflictingKeys(fields),
                type: .span
            )
        }
    }

    /// Signals that the operation described by this span has now ended. It automatically records its
    /// duration up to this point.
    ///
    /// - note: An operation can be ended only once, consecutive calls to this method
    /// have no effect.
    ///
    /// - parameter result:          The result of the operation.
    /// - parameter file:            The unique file identifier that has the form module/file.
    /// - parameter line:            The line number where the log is emitted.
    /// - parameter function:        The name of the function from which the log is emitted.
    /// - parameter fields:          The extra fields to include with the log.
    /// - parameter endTimeInterval: An optional custom end time to use in combination with the `startTimeInterval`
    ///                              provided when creating the span. Setting one and not the other is considered an error
    ///                              and in that scenario, Capture's time provider will be used instead.
    public func end(
        _ result: SpanResult,
        file: String? = #file,
        line: Int? = #line,
        function: String? = #function,
        fields: Fields? = nil,
        endTimeInterval: TimeInterval? = nil
    ) {
        self.logger.update { loggerWrapper in
            guard let logger = loggerWrapper?.underlyingLogger else {
                return
            }

            loggerWrapper = nil

            let customFields = (self.fields ?? [:]).mergedOverwritingConflictingKeys(fields)

            let fields = self.makeEndEventFields(result: result, endTimeInterval: endTimeInterval)
                .mergedOmittingConflictingKeys(customFields)

            logger.log(
                level: self.logLevel,
                message: "",
                file: file,
                line: line,
                function: function,
                fields: fields,
                type: .span
            )
        }
    }

    deinit {
        self.end(.unknown, file: nil, line: nil, function: nil)
    }

    // MARK: - Private

    private func makeStartEventFields() -> Fields {
        return self.makeFields(for: .start)
    }

    private func makeEndEventFields(result: SpanResult, endTimeInterval: TimeInterval?) -> Fields {
        var fields = self.makeFields(for: .end)

        fields["_result"] = switch result {
        case .success:
            "success"
        case .failure:
            "failure"
        case .canceled:
            "canceled"
        case .unknown:
            "unknown"
        }

        if let endTimeInterval, let customStartTimeInterval = self.customStartTimeInterval {
            fields["_duration_ms"] = String((endTimeInterval - customStartTimeInterval) * 1_000)
        } else {
            fields["_duration_ms"] = String(self.timeProvider.timeIntervalSince(self.startedAt) * 1_000)
        }

        return fields
    }

    private func makeFields(for eventType: EventType) -> Fields {
        var fields = [
            "_span_id": self.id.uuidString,
            "_span_name": self.name,
            "_span_type": eventType.rawValue,
        ]

        fields["_span_parent_id"] = self.parentSpanID?.uuidString
        return fields
    }
}

// Introduced as an alternative to `WeakBox<T?> where T: CoreLogging` as using `WeakBox` would require
// the `Span` to be a generic type over `T`.
private final class WeakCoreLogging {
    private(set) weak var underlyingLogger: CoreLogging?

    init(_ logger: CoreLogging) {
        self.underlyingLogger = logger
    }
}
