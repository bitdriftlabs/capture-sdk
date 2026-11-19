// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.common.MainThreadHandler

object Mocks {
    @Suppress("UNCHECKED_CAST")
    val sameThreadHandler: MainThreadHandler by lazy {
        mock {
            on { run(any()) } doAnswer { (it.arguments[0] as Function0<Unit>).invoke() }
            on { runAndReturnResult<Any>(any()) } doAnswer { (it.arguments[0] as Function0<Any>).invoke() }
        }
    }
}
