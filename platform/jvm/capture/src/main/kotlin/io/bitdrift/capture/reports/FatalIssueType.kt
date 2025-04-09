// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

/**
 * Specifies the different types of fatal issues that can occur within the application.
 *
 * Each type represents a specific kind of fatal issue that may trigger the reporting mechanism.
 *
 * @param displayName The readable name of the fatal issue type used for logging or reporting purposes.
 */
enum class FatalIssueType(
    /**
     * The readable name of the fatal issue type.
     */
    val displayName: String,
) {
    /**
     * Represents a JVM crash, typically caused by unhandled exceptions or fatal errors within the JVM.
     */
    JVM_CRASH("jvm_crash"),

    /**
     * Represents a native crash, which could be caused by an issue in native code (e.g., C, C++, rust).
     */
    NATIVE_CRASH("native_crash"),

    /**
     * Represents an ANR (Application Not Responding) error, which occurs when the application is
     * unresponsive for a specific period (usually 5 seconds).
     */
    ANR("anr"),
}
