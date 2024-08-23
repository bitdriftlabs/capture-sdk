// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.attributes.ClientAttributes
import io.bitdrift.capture.attributes.DeviceAttributes
import io.bitdrift.capture.attributes.NetworkAttributes
import io.bitdrift.capture.providers.SystemDateProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ProvidersTest {
    @Test
    fun system_date_provider() {
        // ACT - just exercise the codepath to make sure we're not using invalid APIs for version 23
        val provider = SystemDateProvider()
        val date = provider()

        // ASSERT
        assertThat(date).isNotNull
    }

    @Test
    fun client_attributes() {
        // ARRANGE
        val packageName = "my.bitdrift.test"
        val versionName = "1.2.3.4"
        val versionCode = 66

        val currentState = Lifecycle.State.STARTED
        val lifecycle = mock(Lifecycle::class.java)
        doReturn(currentState).`when`(lifecycle).currentState
        val lifecycleOwner = mock(LifecycleOwner::class.java)
        doReturn(lifecycle).`when`(lifecycleOwner).lifecycle

        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        doReturn(packageName).`when`(context).packageName

        val packageManager: PackageManager = mock(PackageManager::class.java)
        doReturn(packageManager).`when`(context).packageManager

        val packageInfo: PackageInfo = mock(PackageInfo::class.java)
        doReturn(packageInfo).`when`(packageManager).getPackageInfo(packageName, 0)
        packageInfo.versionName = versionName
        packageInfo.versionCode = versionCode

        // ACT
        val clientAttributes = ClientAttributes(context, lifecycleOwner).invoke()

        // ASSERT
        assertThat(clientAttributes).containsEntry("app_version", versionName)
        assertThat(clientAttributes).containsEntry("_app_version_code", versionCode.toString())
        assertThat(clientAttributes).containsEntry("app_id", packageName)
        assertThat(clientAttributes).containsEntry("foreground", "1")
    }

    @Test
    fun client_attributes_not_foreground() {
        // ARRANGE
        val currentState = Lifecycle.State.CREATED
        val lifecycle = mock(Lifecycle::class.java)
        doReturn(currentState).`when`(lifecycle).currentState
        val lifecycleOwner = mock(LifecycleOwner::class.java)
        doReturn(lifecycle).`when`(lifecycleOwner).lifecycle

        val context = spy(ApplicationProvider.getApplicationContext<Context>())

        val packageManager: PackageManager = mock(PackageManager::class.java)
        doReturn(packageManager).`when`(context).packageManager

        val packageInfo: PackageInfo = mock(PackageInfo::class.java)
        doReturn(packageInfo).`when`(packageManager).getPackageInfo("my.bitdrift.test", 0)

        // ACT
        val clientAttributes = ClientAttributes(context, lifecycleOwner).invoke()

        // ASSERT
        assertThat(clientAttributes).containsEntry("foreground", "0")
    }

    @Test
    @Ignore("Robolectric throwing ClassNotFoundException: android.telephony.TelephonyCallback")
    @Suppress("ForbiddenComment")
    fun network_attributes() {
        // TODO: Fix test
        // ARRANGE
        val context = spy(ApplicationProvider.getApplicationContext<Context>())

        // ACT
        val networkAttributes = NetworkAttributes(context).invoke()

        // ASSERT
        assertThat(networkAttributes).containsEntry("carrier", "tbd")
    }

    @Test
    fun device_attributes() {
        // ACT
        val deviceAttributes = DeviceAttributes(ApplicationProvider.getApplicationContext()).invoke()

        // ASSERT
        assertThat(deviceAttributes).containsEntry("model", "robolectric")
        assertThat(deviceAttributes).containsEntry("_locale", "en_US")
    }
}
