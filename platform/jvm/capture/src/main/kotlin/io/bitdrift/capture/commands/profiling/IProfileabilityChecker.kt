// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.commands.profiling

import android.content.Context
import android.os.Build
import io.bitdrift.capture.utils.BuildVersionChecker

/**
 * Whether the system lets this process be profiled. A release build with
 * `<profileable android:enabled="false">` records a trace with no call stacks, so the command fails
 * fast instead. Below Android 15 the check passes, so the profiler reports the platform as
 * unsupported rather than pointing at a manifest change that cannot help.
 */
internal fun interface IProfileabilityChecker {
    fun isProfileable(): Boolean
}

internal class ProfileabilityChecker(
    private val context: Context,
    private val buildVersionChecker: BuildVersionChecker = BuildVersionChecker(),
) : IProfileabilityChecker {
    override fun isProfileable(): Boolean =
        !buildVersionChecker.isAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM) || context.applicationInfo.isProfileable
}
