// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.InternalFieldsMap
import io.bitdrift.capture.LogAttributesOverrides
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.utils.BuildVersionChecker
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import java.util.regex.Matcher
import java.util.regex.Pattern

internal class AppExitLogger(
    private val logger: LoggerImpl,
    private val activityManager: ActivityManager,
    private val runtime: Runtime,
    private val errorHandler: ErrorHandler,
    private val crashHandler: CaptureUncaughtExceptionHandler = CaptureUncaughtExceptionHandler(),
    private val versionChecker: BuildVersionChecker = BuildVersionChecker(),
) {

    companion object {
        const val APP_EXIT_EVENT_NAME = "AppExit"

        // TODO Refactor to trace parser class
        val THREAD_HEADER_PATTERN =
            Pattern.compile(".*----- pid (?<pid>.\\d+) at (?<timeCreated>\\d{4}-\\d{2}-\\d{2}[T ]{0,}[0-9:.-]+) -----(?<body>.*$)")
        val TRACE_THREADS_PATTERN = Pattern.compile(
            ".*DALVIK THREADS \\((?<threadCnt>\\d+)\\):\\s(.*)----- end (\\d+) -----",
            Pattern.MULTILINE
        )
        val THREAD_ID_PATTERN = Pattern.compile("^\"(?<threadName>.*)\" (.*)prio=(\\d+).*$")
    }

    fun installAppExitLogger() {
        if (!runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS)) {
            return
        }
        crashHandler.install(this)
        saveCurrentSessionId()
        logPreviousExitReasonIfAny()
    }

    fun uninstallAppExitLogger() {
        crashHandler.uninstall()
    }

    @TargetApi(Build.VERSION_CODES.R)
    fun saveCurrentSessionId(sessionId: String? = null) {
        if (!runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS) || !versionChecker.isAtLeast(Build.VERSION_CODES.R)) {
            return
        }

        val currentSessionId = sessionId ?: logger.sessionId

        try {
            activityManager.setProcessStateSummary(currentSessionId.toByteArray(StandardCharsets.UTF_8))
        } catch (error: Throwable) {
            // excessive calls to this API could result in a RuntimeException.
            errorHandler.handleError("Failed to save session id in ActivityManager", error)
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    @VisibleForTesting
    internal fun logPreviousExitReasonIfAny() {
        if (!runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS) || !versionChecker.isAtLeast(Build.VERSION_CODES.R)) {
            return
        }

        val exits = try {
            // a null packageName means match all packages belonging to the caller's process (UID)
            // pid should be 0, a value of 0 means to ignore this parameter and return all matching records
            // maxNum should be 1, The maximum number of results to be returned, as we need only the last one
            activityManager.getHistoricalProcessExitReasons(null, 0, 1)
        } catch (error: Throwable) {
            errorHandler.handleError("Failed to retrieve ProcessExitReasons from ActivityManager", error)
            emptyList()
        }
        if (exits.isEmpty()) {
            return
        }

        val lastExitInfo = exits.first()
        // extract stored id from previous session in order to override the log, bail if not present
        val sessionId = lastExitInfo.processStateSummary?.toString(StandardCharsets.UTF_8) ?: return
        val timestampMs = lastExitInfo.timestamp
        logger.log(
            LogType.LIFECYCLE,
            lastExitInfo.reason.toLogLevel(),
            lastExitInfo.toFields(),
            attributesOverrides = LogAttributesOverrides(sessionId, timestampMs),
        ) { APP_EXIT_EVENT_NAME }

        /** FIXME
         * AEI records are maintained in a ring buffer that is updated when ART sees fit,
         * So the _last_ AEI record for this PID may appear over several launches.
         * To avoid over reporting of exit reasons, we need to maintain a history of
         * AEI records visited
         */
    }

    fun logCrash(thread: Thread, throwable: Throwable) {
        if (!runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS)) {
            return
        }

        // explicitly letting it run in the caller thread
        val rootCause = throwable.getRootCause()
        logger.log(
            LogType.LIFECYCLE,
            LogLevel.ERROR,
            mapOf(
                "_app_exit_source" to "UncaughtExceptionHandler",
                "_app_exit_reason" to "Crash",
                "_app_exit_info" to rootCause.javaClass.name,
                "_app_exit_details" to rootCause.message.orEmpty(),
                "_app_exit_thread" to thread.name,
            ).toFields(),
            blocking = true, // this ensures we block until the log has been persisted to disk
        ) {
            APP_EXIT_EVENT_NAME
        }

        if (runtime.isEnabled(RuntimeFeature.LOGGER_FLUSHING_ON_CRASH)) {
            logger.flush(true) // wait for state to be flushed to disk
        }
    }

    private fun Throwable.getRootCause(): Throwable {
        var error = this
        while (error.cause != null) {
            error = error.cause!!
        }
        if (error is InvocationTargetException) {
            error = error.targetException
        }
        return error
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.toFields(): InternalFieldsMap {
        // https://developer.android.com/reference/kotlin/android/app/ApplicationExitInfo

        val appExitProps = mapOf(
            "_app_exit_source" to "ApplicationExitInfo",
            "_app_exit_process_name" to this.processName,
            "_app_exit_reason" to this.reason.toReasonText(),
            "_app_exit_importance" to this.importance.toImportanceText(),
            "_app_exit_status" to this.status.toString(),
            "_app_exit_pss" to this.pss.toString(),
            "_app_exit_rss" to this.rss.toString(),
            "_app_exit_description" to this.description.orEmpty(),
        )

        appExitProps.plus(decomposeSystemTraceToFieldValues(this))

        return appExitProps.toFields()
    }

    private fun Int.toReasonText(): String {
        return when (this) {
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_OTHER -> "OTHER"
            ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
            else -> "UNKNOWN"
        }
    }

    private fun Int.toImportanceText(): String {
        return when (this) {
            RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
            RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> "CANT_SAVE_STATE"
            RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
            RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
            RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
            RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
            RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
            RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "TOP_SLEEPING"
            RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
            else -> "UNKNOWN"
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun Int.toLogLevel(): LogLevel {
        return when (this) {
            in listOf(
                ApplicationExitInfo.REASON_CRASH,
                ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_ANR,
                ApplicationExitInfo.REASON_LOW_MEMORY,
            ),
            -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
    }

    /**
     * For AEI reason types that support it (ANR and NATIVE_CRASH), read the options trace file provided through
     * the getTraceInputStream() method. Parse out the interesting bits from the sys trace
     *
     * @param appExitInfo ApplicationExitInfo record provided by ART
     * @return InternalFieldsMap containing any harvested data
     */
    @SuppressLint("SwitchIntDef")
    @TargetApi(Build.VERSION_CODES.R)
    @VisibleForTesting
    fun decomposeSystemTraceToFieldValues(appExitInfo: ApplicationExitInfo): InternalFieldsMap {
        val traceFields = buildMap<String, FieldValue> { }.toMutableMap()

        when (appExitInfo.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR -> {
                var sysTrace =
                    appExitInfo.traceInputStream?.bufferedReader().use { it?.readText() ?: "" }

                if (sysTrace.isNotBlank()) {
                    // replace newlines with tabs to parse the entire trace as a tab delimited string
                    sysTrace = sysTrace.trim().replace('\n', '\t')

                    // ----- pid 4473 at 2024-02-15 23:37:45.593138790-0800 -----
                    val headerMatcher: Matcher =
                        THREAD_HEADER_PATTERN.matcher(sysTrace)
                    if (headerMatcher.matches()) {
                        traceFields["_app_pid"] =
                            FieldValue.StringField(headerMatcher.group(1)!!.trim())
                        traceFields["_app_timestamp"] =
                            FieldValue.StringField(headerMatcher.group(2)!!.trim())
                    }

                    // DALVIK THREADS (<nThreads>):\n<thread 0>\n<thread 1>...\n<thread n>\n----- end (<pid>) -----
                    val threadsMatcher: Matcher = TRACE_THREADS_PATTERN.matcher(sysTrace)
                    if (threadsMatcher.matches()) {
                        val threadData = threadsMatcher.group(2)!!.trim()
                        traceFields["_app_threads"] =
                            FieldValue.StringField(parseThreadData(threadData).toString())
                    }

                    // TODO Look for additional context info: GC, JNI/JIT, ART internal metrics?
                }
            }
        }

        return traceFields
    }

    @VisibleForTesting
    fun parseThreadData(threadData: String?): List<String> {
        if (!threadData.isNullOrBlank()) {
            val threads = threadData.split("\t\t")
                .filter { THREAD_ID_PATTERN.matcher(it).matches() }
                .map { it.replace("\t", "\n").plus("\n\n") }

            return threads
        }

        return arrayListOf()
    }

}
