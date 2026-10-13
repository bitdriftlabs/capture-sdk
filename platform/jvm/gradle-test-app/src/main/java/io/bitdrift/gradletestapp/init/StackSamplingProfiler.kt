// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.init

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration

/**
 * Captures a Perfetto call stack sampling trace of this process and returns its bytes.
 */
object StackSamplingProfiler {
    sealed interface Result {
        class Trace(
            val bytes: ByteArray,
        ) : Result

        class Failure(
            val reason: String,
        ) : Result
    }

    suspend fun capture(
        context: Context,
        duration: Duration,
    ): Result =
        when (val result = AppProfiler.capture(context, AppProfiler.ProfilingType.STACK_SAMPLING, duration)) {
            is AppProfiler.Result.Failure -> Result.Failure(result.reason)
            is AppProfiler.Result.Saved ->
                withContext(Dispatchers.IO) {
                    File(result.path).takeIf(File::exists)?.readBytes()?.let(Result::Trace)
                        ?: Result.Failure("trace file missing at ${result.path}")
                }
        }
}
