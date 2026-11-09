// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider

/**
 * Sets if the app is debuggable or not
 */
fun setIsDebuggable(debuggable: Boolean) {
    val appInfo = ApplicationProvider.getApplicationContext<android.app.Application>().applicationInfo
    if (debuggable) {
        appInfo.flags = appInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
    } else {
        appInfo.flags = appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }
}
