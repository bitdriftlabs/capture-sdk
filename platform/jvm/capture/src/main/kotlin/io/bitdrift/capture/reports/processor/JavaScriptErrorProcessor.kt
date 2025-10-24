// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Error
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ErrorRelation
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Frame
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.FrameType
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.Report
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.ReportType
import io.bitdrift.capture.reports.binformat.v1.issue_reporting.SourceFile

/**
 * Process JavaScript errors into a binary flatbuffer Report
 */
internal object JavaScriptErrorProcessor {
    /**
     * Parse a JavaScript error stack trace and create a Report
     *
     * @param builder FlatBufferBuilder to construct the report
     * @param sdk SDK info offset
     * @param appMetrics App metrics offset
     * @param deviceMetrics Device metrics offset
     * @param rawStackTrace Raw JavaScript stack trace string
     * @return byte offset for the Report instance in the builder buffer
     */
    fun getJavaScriptErrorReport(
        builder: FlatBufferBuilder,
        sdk: Int,
        appMetrics: Int,
        deviceMetrics: Int,
        rawStackTrace: String,
    ): Int {
        val parsedError = parseJavaScriptStackTrace(rawStackTrace)
        val errors = buildErrors(builder, parsedError)

        return Report.createReport(
            builder,
            sdk,
            ReportType.JavaScriptNonFatalError,
            appMetrics,
            deviceMetrics,
            Report.createErrorsVector(builder, errors.toIntArray()),
            threadDetailsOffset = 0, // JS is single-threaded in this context
            binaryImagesOffset = 0,
            stateOffset = 0,
            featureFlagsOffset = 0,
        )
    }

    private fun buildErrors(
        builder: FlatBufferBuilder,
        parsedError: ParsedJavaScriptError,
    ): List<Int> {
        val frames = parsedError.frames.map { frame -> buildFrame(builder, frame) }.toIntArray()
        val errorName = builder.createString(parsedError.name)
        val errorMessage = builder.createString(parsedError.message)

        return listOf(
            Error.createError(
                builder,
                errorName,
                errorMessage,
                Error.createStackTraceVector(builder, frames),
                ErrorRelation.CausedBy,
            ),
        )
    }

    private fun buildFrame(
        builder: FlatBufferBuilder,
        frame: JavaScriptFrame,
    ): Int {
        val symbolNameOffset = builder.createString(frame.functionName)
        val sourceFileOffset = buildSourceFile(builder, frame.fileName, frame.lineNumber, frame.columnNumber)
        val jsBundlePathOffset = frame.bundlePath?.let { builder.createString(it) } ?: 0

        return Frame.createFrame(
            builder,
            type = FrameType.JavaScript,
            classNameOffset = 0, // JS doesn't have classes in stack traces
            symbolNameOffset = symbolNameOffset,
            sourceFileOffset = sourceFileOffset,
            imageIdOffset = 0,
            frameAddress = 0u,
            symbolAddress = 0u,
            registersOffset = 0,
            stateOffset = 0,
            frameStatus = 0,
            originalIndex = 0u,
            inApp = true, // Assume app code unless we determine otherwise
            symbolicatedNameOffset = 0,
            jsBundlePathOffset = jsBundlePathOffset,
        )
    }

    private fun buildSourceFile(
        builder: FlatBufferBuilder,
        fileName: String?,
        lineNumber: Long?,
        columnNumber: Long?,
    ): Int {
        if (fileName == null) return 0

        val path = builder.createString(fileName)
        return SourceFile.createSourceFile(
            builder,
            path,
            lineNumber ?: 0,
            columnNumber ?: 0,
        )
    }

    /**
     * Parse a JavaScript stack trace string into structured data
     *
     * Expected format:
     * ```
     * Error: Message
     *     at functionName (http://localhost:8081/index.bundle//&params:line:column)
     *     at anotherFunction (http://localhost:8081/index.bundle//&params:line:column)
     * ```
     */
    private fun parseJavaScriptStackTrace(rawStackTrace: String): ParsedJavaScriptError {
        val lines = rawStackTrace.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return ParsedJavaScriptError("Unknown", "No stack trace available", emptyList())
        }

        // First line is the error message: "Error: Message"
        val firstLine = lines.first()
        val (name, message) = parseErrorLine(firstLine)

        // Remaining lines are stack frames
        val frames = lines.drop(1).mapNotNull { line -> parseStackFrame(line) }

        return ParsedJavaScriptError(name, message, frames)
    }

    private fun parseErrorLine(line: String): Pair<String, String> {
        val colonIndex = line.indexOf(':')
        return if (colonIndex > 0) {
            val name = line.substring(0, colonIndex).trim()
            val message = line.substring(colonIndex + 1).trim()
            name to message
        } else {
            "Error" to line
        }
    }

    /**
     * Parse a single stack frame line
     *
     * Examples:
     * - "at functionName (http://localhost:8081/index.bundle//&params:118064:20)"
     * - "at forEach (native)"
     * - "at anonymous (http://...)"
     */
    private fun parseStackFrame(line: String): JavaScriptFrame? {
        if (!line.startsWith("at ")) return null

        val content = line.substring(3).trim()

        // Check for native frames: "at forEach (native)"
        if (content.endsWith("(native)")) {
            val functionName = content.substringBefore(" (native)").trim()
            return JavaScriptFrame(
                functionName = functionName,
                fileName = "[native code]",
                lineNumber = null,
                columnNumber = null,
                bundlePath = null,
            )
        }

        // Parse format: "functionName (url:line:column)"
        val openParenIndex = content.lastIndexOf('(')
        val closeParenIndex = content.lastIndexOf(')')

        if (openParenIndex == -1 || closeParenIndex == -1) {
            // Malformed frame
            return null
        }

        val functionName = content.substring(0, openParenIndex).trim()
        val location = content.substring(openParenIndex + 1, closeParenIndex)

        return parseLocation(location, functionName)
    }

    /**
     * Parse the location part of a stack frame
     *
     * Example: "http://localhost:8081/index.bundle//&platform=android&dev=true:118064:20"
     * Extracts:
     * - bundlePath: "/index.bundle"
     * - fileName: "index.bundle"
     * - lineNumber: 118064
     * - columnNumber: 20
     */
    private fun parseLocation(
        location: String,
        functionName: String,
    ): JavaScriptFrame {
        // Split by ':' from the end to get line and column numbers
        val parts = location.split(':')

        val columnNumber = parts.lastOrNull()?.toLongOrNull()
        val lineNumber = if (parts.size >= 2) parts[parts.size - 2].toLongOrNull() else null

        // The URL is everything except the last two parts (line:column)
        val urlPart =
            if (parts.size >= 3) {
                parts.dropLast(2).joinToString(":")
            } else {
                location
            }

        // Extract bundle path from URL
        // Example: "http://localhost:8081/index.bundle//&params" -> "/index.bundle"
        val bundlePath = extractBundlePath(urlPart)
        val fileName = bundlePath?.substringAfterLast('/') ?: urlPart

        return JavaScriptFrame(
            functionName = functionName,
            fileName = fileName,
            lineNumber = lineNumber,
            columnNumber = columnNumber,
            bundlePath = bundlePath,
        )
    }

    /**
     * Extract the bundle path from a full URL
     *
     * Example: "http://localhost:8081/index.bundle//&platform=..." -> "/index.bundle"
     */
    private fun extractBundlePath(url: String): String? {
        // Find the protocol end
        val protocolEnd = url.indexOf("://")
        if (protocolEnd == -1) return null

        // Find the first '/' after the host
        val pathStart = url.indexOf('/', protocolEnd + 3)
        if (pathStart == -1) return null

        // Find the end of the bundle path (before query params marked by '//')
        val queryStart = url.indexOf("//", pathStart)
        val pathEnd = if (queryStart != -1) queryStart else url.length

        return url.substring(pathStart, pathEnd)
    }
}

/**
 * Represents a parsed JavaScript error with structured frame data
 */
private data class ParsedJavaScriptError(
    val name: String,
    val message: String,
    val frames: List<JavaScriptFrame>,
)

/**
 * Represents a single JavaScript stack frame
 */
private data class JavaScriptFrame(
    val functionName: String,
    val fileName: String?,
    val lineNumber: Long?,
    val columnNumber: Long?,
    val bundlePath: String?,
)
