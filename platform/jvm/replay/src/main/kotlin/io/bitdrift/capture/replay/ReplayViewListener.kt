// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.replay

import android.view.View

/**
 * Listener interface for receiving callbacks when views are encountered
 * during session replay view traversal.
 */
interface ReplayViewListener {
    /**
     * Called when a view is encountered during view traversal.
     * This is called on the main thread.
     *
     * @param view The View that was found
     */
    fun onViewFound(view: View)
}
