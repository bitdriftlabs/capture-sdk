// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import android.content.Context
import android.content.pm.ApplicationInfo

internal object BuildTypeChecker {
    /**
     * Determine if app is debuggable using this bitwise operation
     */
    fun isDebuggable(appContext: Context): Boolean = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
