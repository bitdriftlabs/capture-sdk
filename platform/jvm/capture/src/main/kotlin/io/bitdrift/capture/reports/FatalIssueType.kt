// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

/**
 * Represents all different type of FatalIssues
 *  @property readableType The readable fatal issue type
 *
 */
enum class FatalIssueType(
    val readableType: String,
) {
    /**
     * Represents Application Not Responding fatal issues
     */
    ANR("ANR"),

    /**
     * Represents "regular" JVM crashes
     */
    JVM_CRASH("JVM_CRASH"),

    /**
     * Represents native crashes
     */
    NATIVE_CRASH("NATIVE_CRASH"),
}
