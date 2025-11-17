// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.model

/** Diagnostics/testing feature state */
data class DiagnosticsState(
    val selectedAppExitReason: AppExitReason = AppExitReason.APP_CRASH_REGULAR_JVM_EXCEPTION,
)

/**
 * Represents different ways the app can exit for testing purposes
 */
enum class AppExitReason {
    ANR_BLOCKING_GET,
    ANR_BROADCAST_RECEIVER,
    ANR_COROUTINES,
    ANR_DEADLOCK,
    ANR_GENERIC,
    ANR_SLEEP_MAIN_THREAD,
    APP_CRASH_COROUTINE_EXCEPTION,
    APP_CRASH_NESTED_COROUTINE_EXCEPTION,
    APP_CRASH_REGULAR_JVM_EXCEPTION,
    APP_CRASH_RX_JAVA_EXCEPTION,
    APP_CRASH_OUT_OF_MEMORY,
    NATIVE_CAPTURE_DESTROY_CRASH,
    NATIVE_SIGSEGV,
    NATIVE_SIGBUS,
    SYSTEM_EXIT,
}
