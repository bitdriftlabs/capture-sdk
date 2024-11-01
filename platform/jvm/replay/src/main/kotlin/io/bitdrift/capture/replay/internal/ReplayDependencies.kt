// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay.internal

import io.bitdrift.capture.common.ErrorHandler
import io.bitdrift.capture.replay.ReplayLogger
import io.bitdrift.capture.replay.SessionReplayConfiguration

internal class ReplayDependencies(
    val errorHandler: ErrorHandler,
    val logger: ReplayLogger,
    val sessionReplayConfiguration: SessionReplayConfiguration,
) {
    val displayManager: DisplayManagers = DisplayManagers()

    val replayCapture: ReplayCapture by lazy {
        ReplayCapture()
    }
}
