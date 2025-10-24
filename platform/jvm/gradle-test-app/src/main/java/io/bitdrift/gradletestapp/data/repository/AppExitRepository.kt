// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.repository

import android.content.Context
import io.bitdrift.gradletestapp.data.model.AppExitReason
import io.bitdrift.gradletestapp.diagnostics.fatalissues.FatalIssueGenerator
import kotlin.system.exitProcess

/**
 * Handles different AppExitReason actions
 */
class AppExitRepository {
    fun triggerAppExit(
        applicationContext: Context,
        reason: AppExitReason,
    ) {
        when (reason) {
            AppExitReason.JAVA_SCRIPT_ERROR -> { /*To implement*/ }
            AppExitReason.ANR_BLOCKING_GET -> FatalIssueGenerator.forceBlockingGetAnr()
            AppExitReason.ANR_BROADCAST_RECEIVER ->
                FatalIssueGenerator.forceBroadcastReceiverAnr(
                    applicationContext,
                )

            AppExitReason.ANR_COROUTINES -> FatalIssueGenerator.forceCoroutinesAnr()
            AppExitReason.ANR_DEADLOCK -> FatalIssueGenerator.forceDeadlockAnr()
            AppExitReason.ANR_GENERIC -> FatalIssueGenerator.forceGenericAnr(applicationContext)
            AppExitReason.ANR_SLEEP_MAIN_THREAD -> FatalIssueGenerator.forceThreadSleepAnr()
            AppExitReason.APP_CRASH_COROUTINE_EXCEPTION -> FatalIssueGenerator.forceCoroutinesCrash()
            AppExitReason.APP_CRASH_REGULAR_JVM_EXCEPTION -> FatalIssueGenerator.forceUnhandledException()
            AppExitReason.APP_CRASH_RX_JAVA_EXCEPTION -> FatalIssueGenerator.forceRxJavaException()
            AppExitReason.APP_CRASH_OUT_OF_MEMORY -> FatalIssueGenerator.forceOutOfMemoryCrash()
            AppExitReason.NATIVE_CAPTURE_DESTROY_CRASH -> FatalIssueGenerator.forceCaptureNativeCrash()
            AppExitReason.NATIVE_SIGSEGV -> FatalIssueGenerator.forceNativeSegmentationFault()
            AppExitReason.NATIVE_SIGBUS -> FatalIssueGenerator.forceNativeBusError()
            AppExitReason.SYSTEM_EXIT -> exitProcess(0)
        }
    }
}
