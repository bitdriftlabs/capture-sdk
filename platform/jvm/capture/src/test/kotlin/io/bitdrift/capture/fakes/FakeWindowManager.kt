// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.fakes

import android.app.Activity
import android.view.View
import io.bitdrift.capture.common.IWindowManager

/**
 * [IWindowManager] fake. Tests that need an "activity was already started before the SDK" state
 * assign [firstValidActivity]; everything else reads as an app with no windows.
 */
internal class FakeWindowManager(
    var firstValidActivity: Activity? = null,
) : IWindowManager {
    override fun getBottomMostRootView(): View? = null

    override fun getAllRootViews(): List<View> = emptyList()

    override fun findFirstValidActivity(): Activity? = firstValidActivity
}
