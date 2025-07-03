// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * Holds relevant SDK info upon successful start
 */
data class StartedSdk(
    /**
     * The valid logger instance.
     *
     * You can access fields like logger.sessionId, logger.sessionUrl, logger.device, etc
     */
    val logger: ILogger,
)
