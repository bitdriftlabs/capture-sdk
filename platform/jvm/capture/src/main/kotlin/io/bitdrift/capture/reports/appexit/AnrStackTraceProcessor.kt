// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.appexit

import io.bitdrift.capture.reports.FatalIssueType
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Processes ApplicationExitInfo.inputStream from REASON_ANR to remove unnecessary information
 */
internal object AnrStackTraceProcessor : AppExitStackTraceProcessor {
    private const val BEGIN_MAIN_TRACE_KEYWORD = "\"main\""
    private const val TRACE_END_KEYWORD = "- end"
    private const val UNATTACHED_THREADS_KEYWORD = "(not attached)"
    private const val INVALID_THREAD_PART_IDENTIFIER = "|"

    override fun process(traceInputStream: InputStream): ProcessedResult {
        try {
            val processedAnrTrace = StringBuilder()
            BufferedReader(InputStreamReader(traceInputStream, Charset.defaultCharset())).use { reader ->
                val relevantLines = readRelevantLines(reader)
                relevantLines.forEach {
                    processedAnrTrace.append(it).append(System.lineSeparator())
                }
            }
            return ProcessedResult.Success(processedAnrTrace.toString(), FatalIssueType.ANR)
        } catch (ioException: IOException) {
            return ProcessedResult
                .Failed("Error processing ANR trace in AnrStackTraceProcessor. ${ioException.message}")
        }
    }

    private fun readRelevantLines(reader: BufferedReader): List<String> {
        val relevantLines = mutableListOf<String>()
        var isRelevant = false
        reader.lineSequence().forEach { line ->
            if (line.startsWith(BEGIN_MAIN_TRACE_KEYWORD)) isRelevant = true
            if (isEndOfRelevantStackTrace(line)) return relevantLines
            if (isRelevant && !shouldIgnoreNoisyTraceLine(line)) relevantLines.add(line)
        }
        return relevantLines
    }

    private fun shouldIgnoreNoisyTraceLine(line: String): Boolean = line.contains(INVALID_THREAD_PART_IDENTIFIER)

    private fun isEndOfRelevantStackTrace(line: String): Boolean =
        line.contains(TRACE_END_KEYWORD) || line.contains(UNATTACHED_THREADS_KEYWORD)
}
