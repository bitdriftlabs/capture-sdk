// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.annotation.SuppressLint
import android.content.Context
import androidx.startup.Initializer

/**
 * Allows Bitdrift to be initialized with the host ApplicationContext instance
 */
class ContextHolder : Initializer<ContextHolder> {
    override fun create(context: Context): ContextHolder {
        // needs to be the applicationContext to avoid the StaticFieldLeak below
        APP_CONTEXT = context.applicationContext
        return this
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        internal lateinit var APP_CONTEXT: Context

        /**
         * Returns true after the ContextHolder has been initialized and the APP_CONTEXT is available.
         */
        val isInitialized get() = Companion::APP_CONTEXT.isInitialized
    }
}
