// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.attributes.DeviceAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class DeviceAttributesTest {

    @Test
    fun model() {
        val deviceAttributes = DeviceAttributes(ApplicationProvider.getApplicationContext()).invoke()

        assertThat(deviceAttributes).containsEntry("model", "robolectric")
    }

    @Test
    fun locale() {
        val deviceAttributes = DeviceAttributes(ApplicationProvider.getApplicationContext()).invoke()

        assertThat(deviceAttributes).containsEntry("_locale", "en_US")
    }
}
