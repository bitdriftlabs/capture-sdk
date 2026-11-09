// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.providers.fieldsOf
import java.io.File

private const val THREAD_COUNT_KEY = "_thread_count"
private const val PROC_SELF_TASK_PATH = "/proc/self/task"

/**
 * Responsible for collecting the number of threads in the current process. Each thread has an
 * entry under [taskDir], so the thread count is the number of entries in that directory.
 */
internal class ThreadCountMonitor(
    private val taskDir: File = File(PROC_SELF_TASK_PATH),
) {
    var runtime: Runtime? = null

    /**
     * Returns the `_thread_count` field, or [ArrayFields.EMPTY] when the feature is disabled or
     * the thread count cannot be read.
     */
    fun getThreadCount(): ArrayFields {
        if (runtime?.isEnabled(RuntimeFeature.THREAD_COUNT_FIELDS) == false) {
            return ArrayFields.EMPTY
        }

        val threadCount = runCatching { taskDir.list()?.size }.getOrNull() ?: return ArrayFields.EMPTY

        return fieldsOf(THREAD_COUNT_KEY to threadCount.toString())
    }
}
