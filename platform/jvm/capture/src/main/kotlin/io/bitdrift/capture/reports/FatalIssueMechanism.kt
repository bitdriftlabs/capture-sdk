// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports

/**
 * Specifies the fatal issue reporting mechanism. mechanism.
 *
 */
sealed class FatalIssueMechanism(
    /**
     * The deobfuscated name used for logging
     */
    val displayName: String,
) {
    /**
     * Built-in fatal issue reporter implementation that doesn't rely on any 3rd party integration
     */
    data object BuiltIn : FatalIssueMechanism("BUILT_IN")

    /**
     * Internal fallback mechanism when FatalIssueReporting is never set
     */
    internal data object None : FatalIssueMechanism("NONE")
}
