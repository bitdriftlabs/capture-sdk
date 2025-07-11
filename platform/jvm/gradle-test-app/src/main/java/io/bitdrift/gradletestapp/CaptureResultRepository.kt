// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp

import androidx.lifecycle.ViewModel
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.ILogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object CaptureResultRepository : ViewModel() {
    private val _captureResult = MutableSharedFlow<CaptureResult<ILogger>>(replay = 1)
    val captureResult: SharedFlow<CaptureResult<ILogger>> = _captureResult

    fun updateResult(newResult: CaptureResult<ILogger>) {
        _captureResult.tryEmit(newResult)
    }
}
