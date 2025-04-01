// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.persistence

import android.app.ApplicationExitInfo
import com.google.gson.Gson
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.reports.Exception
import io.bitdrift.capture.reports.FatalIssueReport
import io.bitdrift.capture.reports.SourceFile
import io.bitdrift.capture.reports.StackTraceElementData
import io.bitdrift.capture.reports.ThreadData
import io.bitdrift.capture.reports.ThreadMetrics
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/***
 * Persists into disk (at [destinationPath]) a FatalIssueReport (JVM crash/Native crash/ANR)
 */
class FatalIssueReporterStorage(
    private val destinationPath: File,
) {
    private val gson by lazy { Gson() }

    /**
     * TBF
     */
    fun persistAppExitReport(
        errorHandler: ErrorHandler,
        timestamp: Long,
        exitReasonType: Int,
        traceInputStream: InputStream,
    ) {
        if(exitReasonType == ApplicationExitInfo.REASON_ANR){
            try {
                val reader = BufferedReader(InputStreamReader(traceInputStream))
                val stringBuilder = StringBuilder()
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line).append(System.lineSeparator())
                    line = reader.readLine()
                }
                val fileName = "${timestamp}_anr_details.txt"
                val outputFile = File(destinationPath, fileName)
                outputFile.bufferedWriter().use { writer ->
                    writer.write(stringBuilder.toString())
                }
            } catch (ioException: IOException) {
                errorHandler.handleError("Cannot process ANR trace", ioException)
                println("Error while creating the ANR trace file: ${ioException.message}")
            }
        }
    }

    /**
     * TBF
     */
    fun persistJvmCrash(
        errorHandler: ErrorHandler,
        timestamp: Long,
        callerThread: Thread,
        throwable: Throwable,
    ) {
        try {
            val gsonData = convertToGson(callerThread, throwable)
            val fileName = "${timestamp}_crash_details.json"
            val outputFile = File(destinationPath, fileName)
            outputFile.writeText(gson.toJson(gsonData))
        } catch (throwable: Throwable) {
            errorHandler.handleError("Couldn't persist into disk at persistJvmCrash", throwable)
        }
    }

    private fun convertToGson(
        callerThread: Thread,
        throwable: Throwable
    ): FatalIssueReport {
        val stackTraceElements = throwable.stackTrace.map {
            StackTraceElementData(
                symbolName = "${it.className}.${it.methodName}",
                sourceFile = SourceFile(
                    path = it.fileName ?: "Unknown",
                    line = it.lineNumber
                )
            )
        }

        val exceptionData = Exception(
            name = throwable.javaClass.name,
            reason = throwable.message ?: "n/a",
            stackTrace = stackTraceElements
        )

        val threadData = ThreadData(
            name = callerThread.name,
            active = true,
            index = 0,
            state = callerThread.state.name,
            stackTrace = emptyList()
        )
        val fatalIssueReportData = FatalIssueReport(
            issueType = "jvm_crash",
            errors = listOf(exceptionData),
            threads = ThreadMetrics(count = 1, threads = listOf(threadData))
        )
        return fatalIssueReportData
    }
}
