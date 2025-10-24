// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

/**
 * A configuration used to configure bitdrift session replay feature.
 *
 * @param categorizers Map used to matching third party Android views to bitdrift view types.
 */
data class SessionReplayConfiguration
    @JvmOverloads
    constructor(
        val categorizers: Map<ReplayType, List<String>>? = null,
    )

sealed class SessionReplayOptions {
    object Disabled : SessionReplayOptions()

    data class Enabled(
        val categorizers: Map<ReplayType, List<String>>? = null,
    ) : SessionReplayOptions()
}
