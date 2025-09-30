// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.attributes

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.MoreExecutors
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class NetworkAttributesTest {
    @Test
    fun carrier() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val networkAttributes = buildNetworkAttributes(context)

        val result = networkAttributes.invoke()

        assertThat(result).containsEntry("carrier", "")
    }

    @Test
    fun network_type_access_network_state_granted() {
        grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        val context = ApplicationProvider.getApplicationContext<Context>()

        val networkAttributes = NetworkAttributes(context, MoreExecutors.newDirectExecutorService()).invoke()

        assertThat(networkAttributes).containsEntry("network_type", "wwan")
    }

    @Test
    fun network_type_access_network_state_not_granted() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val networkAttributes = NetworkAttributes(context, MoreExecutors.newDirectExecutorService()).invoke()

        assertThat(networkAttributes).doesNotContainKey("network_type")
    }

    @Test
    fun network_type_access_network_state_granted_null_network_capabilities() {
        grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val mockedConnectivityManager = obtainMockedConnectivityManager(context)
        val mockedActiveNetwork = obtainMockedActiveNetwork(mockedConnectivityManager)
        doReturn(null).`when`(mockedConnectivityManager).getNetworkCapabilities(eq(mockedActiveNetwork))

        val networkAttributes = NetworkAttributes(context, MoreExecutors.newDirectExecutorService()).invoke()

        assertThat(networkAttributes).containsEntry("network_type", "unknown")
    }

    @Test
    fun network_type_access_network_state_granted_register_network_callback() {
        grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val mockedConnectivityManager = obtainMockedConnectivityManager(context)

        NetworkAttributes(context, MoreExecutors.newDirectExecutorService()).invoke()

        verify(mockedConnectivityManager).registerDefaultNetworkCallback(
            any(ConnectivityManager.NetworkCallback::class.java),
        )
    }

    @Test
    fun radio_type_read_phone_state_granted() {
        grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val networkAttributes = buildNetworkAttributes(context)

        val result = networkAttributes.invoke()

        assertThat(result).containsEntry("radio_type", "unknown")
    }

    @Test
    fun radio_type_read_phone_state_not_granted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val networkAttributes = buildNetworkAttributes(context)

        val result = networkAttributes.invoke()

        assertThat(result).containsEntry("radio_type", "forbidden")
    }

    @Test
    fun invoke_whenWifiNetworkTransport_shouldMatchType() {
        val fields = invokeWithNetworkCapabilities(NetworkTransport.WIFI)

        assertThat(fields).containsEntry("network_type", "wlan")
    }

    @Test
    fun invoke_whenCellularNetworkTransport_shouldMatchType() {
        val fields = invokeWithNetworkCapabilities(NetworkTransport.CELLULAR)

        assertThat(fields).containsEntry("network_type", "wwan")
    }

    @Test
    fun invoke_whenEthernetNetworkTransport_shouldMatchType() {
        val fields = invokeWithNetworkCapabilities(NetworkTransport.ETHERNET)

        assertThat(fields).containsEntry("network_type", "ethernet")
    }

    @Test
    fun invoke_whenOtherNetworkTransport_shouldMatchType() {
        val fields = invokeWithNetworkCapabilities(NetworkTransport.OTHER)

        assertThat(fields).containsEntry("network_type", "other")
    }

    @Test
    fun invoke_whenLte_shouldMatchType() {
        grantPermissions(Manifest.permission.READ_PHONE_STATE)

        val result =
            invokeWithNetworkCapabilities(
                radioType = TelephonyManager.NETWORK_TYPE_LTE,
            )

        assertThat(result).containsEntry("radio_type", "lte")
    }

    @Test
    fun invoke_whenGsm_shouldMatchType() {
        grantPermissions(Manifest.permission.READ_PHONE_STATE)

        val result =
            invokeWithNetworkCapabilities(
                radioType = TelephonyManager.NETWORK_TYPE_GSM,
            )

        assertThat(result).containsEntry("radio_type", "gsm")
    }

    private fun invokeWithNetworkCapabilities(
        transport: NetworkTransport = NetworkTransport.WIFI,
        radioType: Int = TelephonyManager.NETWORK_TYPE_UNKNOWN,
    ): Map<String, String> {
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val networkAttributes = NetworkAttributes(context, MoreExecutors.newDirectExecutorService())
        val network = mock(Network::class.java)
        val capabilities = mock(NetworkCapabilities::class.java)
        val telephonyManager = mock(TelephonyManager::class.java)

        `when`(capabilities.hasTransport(TRANSPORT_WIFI)).thenReturn(transport == NetworkTransport.WIFI)
        `when`(capabilities.hasTransport(TRANSPORT_CELLULAR)).thenReturn(transport == NetworkTransport.CELLULAR)
        `when`(capabilities.hasTransport(TRANSPORT_ETHERNET)).thenReturn(transport == NetworkTransport.ETHERNET)
        `when`(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager)
        `when`(telephonyManager.networkType).thenReturn(radioType)

        networkAttributes.onCapabilitiesChanged(network, capabilities)
        return networkAttributes.invoke()
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

    private fun buildNetworkAttributes(context: Context): NetworkAttributes {
        val networkAttributes = NetworkAttributes(context, MoreExecutors.newDirectExecutorService())
        val capabilities = NetworkCapabilities()
        val network = mock(Network::class.java)
        networkAttributes.onCapabilitiesChanged(network, capabilities)
        return networkAttributes
    }

    private enum class NetworkTransport {
        WIFI,
        CELLULAR,
        ETHERNET,
        OTHER,
    }
}
