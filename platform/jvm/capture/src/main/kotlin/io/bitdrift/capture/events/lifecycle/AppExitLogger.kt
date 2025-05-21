// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import io.bitdrift.capture.InternalFieldsMap
import io.bitdrift.capture.LogAttributesOverrides
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.performance.IMemoryMetricsProvider
import io.bitdrift.capture.providers.toFields
import io.bitdrift.capture.reports.FatalIssueMechanism
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import io.bitdrift.capture.reports.jvmcrash.CaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.JvmCrashListener
import io.bitdrift.capture.threading.CaptureDispatchers
import io.bitdrift.capture.utils.BuildVersionChecker
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets

internal class AppExitLogger(
    private val logger: LoggerImpl,
    private val activityManager: ActivityManager,
    private val runtime: Runtime,
    private val errorHandler: ErrorHandler,
    private val versionChecker: BuildVersionChecker = BuildVersionChecker(),
    private val memoryMetricsProvider: IMemoryMetricsProvider,
    private val backgroundThreadHandler: IBackgroundThreadHandler = CaptureDispatchers.CommonBackground,
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = LatestAppExitInfoProvider,
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = CaptureUncaughtExceptionHandler,
    private val fatalIssueMechanism: FatalIssueMechanism,
) : JvmCrashListener {
    companion object {
        private const val APP_EXIT_EVENT_NAME = "AppExit"
        private const val APP_EXIT_SOURCE_KEY = "_app_exit_source"
        private const val APP_EXIT_REASON_KEY = "_app_exit_reason"
        private const val APP_EXIT_INFO_KEY = "_app_exit_info"
        private const val APP_EXIT_DETAILS_KEY = "_app_exit_details"
        private const val APP_EXIT_THREAD_KEY = "_app_exit_thread"
        private const val APP_EXIT_PROCESS_NAME_KEY = "_app_exit_process_name"
        private const val APP_EXIT_IMPORTANCE_KEY = "_app_exit_importance"
        private const val APP_EXIT_STATUS_KEY = "_app_exit_status"
        private const val APP_EXIT_PSS_KEY = "_app_exit_pss"
        private const val APP_EXIT_RSS_KEY = "_app_exit_rss"
        private const val APP_EXIT_DESCRIPTION_KEY = "_app_exit_description"
    }

    @WorkerThread
    fun installAppExitLogger() {
        if (!runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS)) {
            return
        }
        backgroundThreadHandler.runAsync {
            captureUncaughtExceptionHandler.install(this)
            saveCurrentSessionId()
            logPreviousExitReasonIfAny()
        }
    }

    fun uninstallAppExitLogger() {
        captureUncaughtExceptionHandler.uninstall()
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

        when (val lastExitInfoResult = latestAppExitInfoProvider.get(activityManager)) {
            is LatestAppExitReasonResult.Empty -> {
                return
            }

            is LatestAppExitReasonResult.Error -> {
                errorHandler.handleError(lastExitInfoResult.message, lastExitInfoResult.throwable)
            }

            is LatestAppExitReasonResult.Valid -> {
                val lastExitInfo = lastExitInfoResult.applicationExitInfo
                // extract stored id from previous session in order to override the log, bail if not present
                val sessionId =
                    lastExitInfo.processStateSummary?.toString(StandardCharsets.UTF_8) ?: return
                val timestampMs = lastExitInfo.timestamp

                logger.log(
                    LogType.LIFECYCLE,
                    lastExitInfo.reason.toLogLevel(),
                    buildAppExitInternalFieldsMap(lastExitInfo),
                    attributesOverrides = LogAttributesOverrides.SessionID(sessionId, timestampMs),
                ) { APP_EXIT_EVENT_NAME }
            }
        }
    }

    override fun onJvmCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        // When FatalIssueMechanism.BuiltIn is configured will rely on shared-core to emit the related JVM crash log
        if (!runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS) || fatalIssueMechanism == FatalIssueMechanism.BuiltIn) {
            return
        }

        // explicitly letting it run in the caller thread
        logger.log(
            LogType.LIFECYCLE,
            LogLevel.ERROR,
            buildCrashAndMemoryFieldsMap(thread, throwable),
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

    private fun buildCrashAndMemoryFieldsMap(
        thread: Thread,
        throwable: Throwable,
    ): InternalFieldsMap {
        val rootCause = throwable.getRootCause()
        return buildMap {
            put(APP_EXIT_SOURCE_KEY, "UncaughtExceptionHandler")
            put(APP_EXIT_REASON_KEY, "Crash")
            put(APP_EXIT_INFO_KEY, rootCause.javaClass.name)
            put(APP_EXIT_DETAILS_KEY, rootCause.message.orEmpty())
            put(APP_EXIT_THREAD_KEY, thread.name)
            putAll(memoryMetricsProvider.getMemoryAttributes())
        }.toFields()
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun buildAppExitInternalFieldsMap(applicationExitInfo: ApplicationExitInfo): InternalFieldsMap =
        buildMap {
            putAll(applicationExitInfo.toMap().toFields())
            putAll(memoryMetricsProvider.getMemoryClass().toFields())
        }

    @TargetApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.toMap(): Map<String, String> {
        // https://developer.android.com/reference/kotlin/android/app/ApplicationExitInfo
        return mapOf(
            APP_EXIT_SOURCE_KEY to "ApplicationExitInfo",
            APP_EXIT_PROCESS_NAME_KEY to this.processName,
            APP_EXIT_REASON_KEY to this.reason.toReasonText(),
            APP_EXIT_IMPORTANCE_KEY to this.importance.toImportanceText(),
            APP_EXIT_STATUS_KEY to this.status.toString(),
            APP_EXIT_PSS_KEY to this.pss.toString(),
            APP_EXIT_RSS_KEY to this.rss.toString(),
            APP_EXIT_DESCRIPTION_KEY to this.description.orEmpty(),
        )
    }

    private fun Int.toReasonText(): String =
        when (this) {
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

    private fun Int.toImportanceText(): String =
        when (this) {
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

    @TargetApi(Build.VERSION_CODES.R)
    private fun Int.toLogLevel(): LogLevel =
        when (this) {
            in
            listOf(
                ApplicationExitInfo.REASON_CRASH,
                ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_ANR,
                ApplicationExitInfo.REASON_LOW_MEMORY,
            ),
            -> LogLevel.ERROR

            else -> LogLevel.INFO
        }
}
