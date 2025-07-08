// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * TBF
 */
internal object ColdStartWatcher {

    private var isColdStartEmitted = false

    fun observe(
        lifecycleOwner: LifecycleOwner,
        onColdStart: () -> Unit,
    ) {
        lifecycleOwner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (!isColdStartEmitted) {
                        isColdStartEmitted = true
                        onColdStart()
                        lifecycleOwner.lifecycle.removeObserver(this)
                    }
                }
            },
        )
    }
}
