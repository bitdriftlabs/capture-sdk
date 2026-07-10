//
//  CustomLogMessage.swift
//  Capture
//
//  Created by Ariel Demarco on 04/05/2026.
//


struct CustomLogMessage: WebViewLoggableMessage, Equatable {
    let tag: String
    let v: Int
    let type: WebViewMessageType
    let timestamp: Int64
    let level: String
    let message: String
    let fields: WebViewSerializableFields?

    func makeLoggingAction(context: WebViewLoggingContext) -> WebViewLoggingAction? {
        var baseFields = makeBaseFields()

        self.fields?.forEach { key, value in
            baseFields[key] = value.fieldStringValue
        }

        let logLevel: LogLevel = switch level.lowercased() {
        case "info": .info
        case "warn": .warning
        case "error": .error
        case "trace": .trace
        default: .debug
        }

        return .log(level: logLevel, message: message, fields: baseFields)
    }
}
