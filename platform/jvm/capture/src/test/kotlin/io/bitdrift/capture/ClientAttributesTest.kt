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
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ClientAttributesTest {

    @Test
    fun foreground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("foreground", "1")
    }

    @Test
    fun not_foreground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockedLifecycleOwnerLifecycleStateCreated = obtainMockedLifecycleOwnerWith(Lifecycle.State.CREATED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateCreated).invoke()

        assertThat(clientAttributes).containsEntry("foreground", "0")
    }

    @Test
    fun app_id() {
        val packageName = "my.bitdrift.test"
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        doReturn(packageName).`when`(context).packageName
        val mockedLifecycleOwnerLifecycleStateStarted =
            obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("app_id", packageName)
    }

    @Test
    fun app_id_unknown() {
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        doReturn(null).`when`(context).packageName
        val mockedLifecycleOwnerLifecycleStateStarted =
            obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("app_id", "unknown")
    }

    @Test
    fun app_version() {
        val versionName = "1.2.3.4"
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val mockedPackageInfo = obtainMockedPackageInfo(context)
        mockedPackageInfo.versionName = versionName
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("app_version", versionName)
    }

    @Test
    fun app_version_unknown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("app_version", "?.?.?")
    }

    @Test
    fun app_version_code() {
        val versionCode = 66
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val mockedPackageInfo = obtainMockedPackageInfo(context)
        mockedPackageInfo.versionCode = versionCode
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("_app_version_code", versionCode.toString())
    }

    @Test
    fun app_version_code_unknown() {
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val packageManager = obtainMockedPackageManager(context)
        doReturn(null).`when`(packageManager).getPackageInfo(anyString(), eq(0))
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("_app_version_code", "-1")
    }

    @Test
    @Config(sdk = [28])
    fun app_version_code_android_pie() {
        val versionCode = 66L
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val mockedPackageInfo = obtainMockedPackageInfo(context)
        doReturn(versionCode).`when`(mockedPackageInfo).longVersionCode
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("_app_version_code", versionCode.toString())
    }

    @Test
    @Config(sdk = [28])
    fun app_version_code_unknown_android_pie() {
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val packageManager = obtainMockedPackageManager(context)
        doReturn(null).`when`(packageManager).getPackageInfo(anyString(), eq(0))
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("_app_version_code", "-1")
    }

    @Test
    @Config(sdk = [33])
    fun package_info_android_tiramisu() {
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val packageManager = obtainMockedPackageManager(context)
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        verify(packageManager).getPackageInfo(anyString(), any(PackageManager.PackageInfoFlags::class.java))
    }

    private fun obtainMockedLifecycleOwnerWith(state: Lifecycle.State): LifecycleOwner {
        val mockedLifecycle = mock(Lifecycle::class.java)
        doReturn(state).`when`(mockedLifecycle).currentState
        val mockedLifecycleOwner = mock(LifecycleOwner::class.java)
        doReturn(mockedLifecycle).`when`(mockedLifecycleOwner).lifecycle
        return mockedLifecycleOwner
    }

    private fun obtainMockedPackageInfo(context: Context): PackageInfo {
        val mockedPackageManager = obtainMockedPackageManager(context)
        val mockedPackageInfo: PackageInfo = mock(PackageInfo::class.java)
        doReturn(mockedPackageInfo).`when`(mockedPackageManager).getPackageInfo(anyString(), eq(0))
        return mockedPackageInfo
    }

    private fun obtainMockedPackageManager(context: Context): PackageManager {
        val mockedPackageManager: PackageManager = mock(PackageManager::class.java)
        doReturn(mockedPackageManager).`when`(context).packageManager
        return mockedPackageManager
    }
}
