// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import io.bitdrift.capture.InternalFields
import io.bitdrift.capture.LogAttributesOverrides
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.LoggerImpl
import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.performance.IMemoryMetricsProvider
import io.bitdrift.capture.providers.combineFields
import io.bitdrift.capture.providers.fieldsOf
import io.bitdrift.capture.reports.FatalIssueReporterState
import io.bitdrift.capture.reports.IFatalIssueReporter
import io.bitdrift.capture.reports.exitinfo.ILatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitInfoProvider
import io.bitdrift.capture.reports.exitinfo.LatestAppExitReasonResult
import io.bitdrift.capture.reports.jvmcrash.CaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.ICaptureUncaughtExceptionHandler
import io.bitdrift.capture.reports.jvmcrash.IJvmCrashListener
import io.bitdrift.capture.utils.BuildVersionChecker

internal class AppExitLogger(
    private val logger: LoggerImpl,
    private val activityManager: ActivityManager,
    private val runtime: Runtime,
    private val errorHandler: ErrorHandler,
    private val versionChecker: BuildVersionChecker = BuildVersionChecker(),
    private val memoryMetricsProvider: IMemoryMetricsProvider,
    private val latestAppExitInfoProvider: ILatestAppExitInfoProvider = LatestAppExitInfoProvider,
    private val captureUncaughtExceptionHandler: ICaptureUncaughtExceptionHandler = CaptureUncaughtExceptionHandler,
    private val fatalIssueReporter: IFatalIssueReporter?,
) : IJvmCrashListener {
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

    @SuppressLint("NewApi")
    fun installAppExitLogger() {
        if (!runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS)) {
            return
        }
        captureUncaughtExceptionHandler.install(this)
        logPreviousExitReasonIfAny()
    }

    fun uninstallAppExitLogger() {
        captureUncaughtExceptionHandler.uninstall()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @VisibleForTesting
    internal fun logPreviousExitReasonIfAny() {
        if (!runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS) || !versionChecker.isAtLeast(Build.VERSION_CODES.R)) {
            return
        }

        when (val lastExitInfoResult = latestAppExitInfoProvider.get(activityManager)) {
            is LatestAppExitReasonResult.Error ->
                errorHandler.handleError(lastExitInfoResult.message, lastExitInfoResult.throwable)

            is LatestAppExitReasonResult.Valid -> {
                val lastExitInfo = lastExitInfoResult.applicationExitInfo

                val timestampMs = lastExitInfo.timestamp
                logger.log(
                    LogType.LIFECYCLE,
                    lastExitInfo.reason.toLogLevel(),
                    buildAppExitInternalFields(lastExitInfo),
                    attributesOverrides = LogAttributesOverrides.PreviousRunSessionId(timestampMs),
                ) { APP_EXIT_EVENT_NAME }
            }

            // No app exit reason available (e.g., first install)
            is LatestAppExitReasonResult.None -> Unit
        }
    }

    override fun onJvmCrash(
        thread: Thread,
        throwable: Throwable,
    ) {
        // When FatalIssueReporterState is Initialized will rely on shared-core to emit the related JVM crash log
        if (!runtime.isEnabled(RuntimeFeature.APP_EXIT_EVENTS) ||
            FatalIssueReporterState.Initialized == fatalIssueReporter?.initializationState()
        ) {
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
        val seenThrowables = mutableSetOf<Throwable>()
        var currentThrowable: Throwable = this

        while (currentThrowable.cause != null) {
            val nextThrowable = currentThrowable.cause!!
            val isAlreadySeen = !seenThrowables.add(nextThrowable)
            if (isAlreadySeen) {
                break
            }
            currentThrowable = nextThrowable
        }
        return currentThrowable
    }

    private fun buildCrashAndMemoryFieldsMap(
        thread: Thread,
        throwable: Throwable,
    ): InternalFields {
        val rootCause = throwable.getRootCause()
        return combineFields(
            fieldsOf(
                APP_EXIT_SOURCE_KEY to "UncaughtExceptionHandler",
                APP_EXIT_REASON_KEY to "Crash",
                APP_EXIT_INFO_KEY to rootCause.javaClass.name,
                APP_EXIT_DETAILS_KEY to rootCause.message.orEmpty(),
                APP_EXIT_THREAD_KEY to thread.name,
            ),
            memoryMetricsProvider.getMemoryAttributes(),
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildAppExitInternalFields(applicationExitInfo: ApplicationExitInfo): InternalFields =
        combineFields(
            applicationExitInfo.toArrayFields(),
            memoryMetricsProvider.getMemoryClass(),
        )

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ApplicationExitInfo.toArrayFields(): InternalFields {
        // https://developer.android.com/reference/kotlin/android/app/ApplicationExitInfo
        return fieldsOf(
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

    @RequiresApi(Build.VERSION_CODES.R)
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
