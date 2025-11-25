// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package com.bdreactnative

import android.content.res.AssetManager

object DebugId {
    // TODO(FranAguilera): BIT-6642 Fully implement debug id generation that should match with the generated sourcemaps
    fun fromBundle(assets: AssetManager): String? {
        return null
    }
}
