// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import io.bitdrift.capture.network.okhttp.DeallocationGuard
import junit.framework.TestCase.fail
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DeallocationGuardTest {

    @Test
    fun deallocationFlowTest() {
        var deallocated = false

        val guard = DeallocationGuard(10) { deallocated = true }

        var called = false
        guard.safeAccess { v -> assertThat(v).isEqualTo(10); called = true }
        assertThat(called).isTrue
        assertThat(deallocated).isFalse

        guard.deallocate()
        assertThat(deallocated).isTrue

        guard.safeAccess { fail("should not be called") }
    }
}
