// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.attributes.NetworkAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class NetworkAttributesTest {

    @Test
    fun carrier() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val networkAttributes = NetworkAttributes(context).invoke()

        assertThat(networkAttributes).containsEntry("carrier", "")
    }

    @Test
    fun network_type_access_network_state_granted() {
        grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        val context = ApplicationProvider.getApplicationContext<Context>()

        val networkAttributes = NetworkAttributes(context).invoke()

        assertThat(networkAttributes).containsEntry("network_type", "wwan")
    }

    @Test
    @Config(sdk = [21])
    fun network_type_access_network_state_granted_android_lollipop() {
        grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        val context = ApplicationProvider.getApplicationContext<Context>()

        val networkAttributes = NetworkAttributes(context).invoke()

        assertThat(networkAttributes).containsEntry("network_type", "other")
    }

    @Test
    fun network_type_access_network_state_not_granted() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val networkAttributes = NetworkAttributes(context).invoke()

        assertThat(networkAttributes).containsEntry("network_type", "unknown")
    }

    @Test
    fun network_type_access_network_state_granted_null_network_capabilities() {
        grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val mockedConnectivityManager = obtainMockedConnectivityManager(context)
        val mockedActiveNetwork = obtainMockedActiveNetwork(mockedConnectivityManager)
        doReturn(null).`when`(mockedConnectivityManager).getNetworkCapabilities(eq(mockedActiveNetwork))

        val networkAttributes = NetworkAttributes(context).invoke()

        assertThat(networkAttributes).containsEntry("network_type", "unknown")
    }

    @Test
    fun network_type_access_network_state_granted_register_network_callback() {
        grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val mockedConnectivityManager = obtainMockedConnectivityManager(context)

        NetworkAttributes(context).invoke()

        verify(mockedConnectivityManager).registerNetworkCallback(
            any(NetworkRequest::class.java),
            any(ConnectivityManager.NetworkCallback::class.java),
        )
    }

    @Test
    fun radio_type_read_phone_state_granted() {
        grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val context = ApplicationProvider.getApplicationContext<Context>()

        val networkAttributes = NetworkAttributes(context).invoke()

        assertThat(networkAttributes).containsEntry("radio_type", "unknown")
    }

    @Test
    fun radio_type_read_phone_state_not_granted() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val networkAttributes = NetworkAttributes(context).invoke()

        assertThat(networkAttributes).containsEntry("radio_type", "forbidden")
    }

    private fun grantPermissions(vararg permissionNames: String) {
        val app = Shadows.shadowOf(RuntimeEnvironment.getApplication())
        app.grantPermissions(*permissionNames)
    }

    private fun obtainMockedConnectivityManager(context: Context): ConnectivityManager {
        val mockedConnectivityManager: ConnectivityManager = mock(ConnectivityManager::class.java)
        doReturn(mockedConnectivityManager).`when`(context).getSystemService(eq(Context.CONNECTIVITY_SERVICE))
        return mockedConnectivityManager
    }

    private fun obtainMockedActiveNetwork(connectivityManager: ConnectivityManager): Network {
        val mockedNetwork: Network = mock(Network::class.java)
        doReturn(mockedNetwork).`when`(connectivityManager).activeNetwork
        return mockedNetwork
    }
}
