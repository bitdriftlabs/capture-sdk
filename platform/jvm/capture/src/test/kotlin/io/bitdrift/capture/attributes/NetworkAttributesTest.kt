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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import io.bitdrift.capture.IInternalLogger
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any as javaAny
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
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
        val logger = buildNetworkAttributes(context)

        verify(logger).updateOotbField("carrier", "")
    }

    @Test
    fun network_type_access_network_state_granted() {
        grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        val context = ApplicationProvider.getApplicationContext<Context>()

        val logger = startNetworkAttributes(context)

        verify(logger).updateOotbField("network_type", "wwan")
    }

    @Test
    fun network_type_access_network_state_not_granted() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val logger = startNetworkAttributes(context)

        verify(logger, never()).updateOotbField(eq("network_type"), any())
    }

    @Test
    fun network_type_access_network_state_granted_null_network_capabilities() {
        grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val mockedConnectivityManager = obtainMockedConnectivityManager(context)
        val mockedActiveNetwork = obtainMockedActiveNetwork(mockedConnectivityManager)
        doReturn(null).`when`(mockedConnectivityManager).getNetworkCapabilities(eq(mockedActiveNetwork))

        val logger = startNetworkAttributes(context)

        verify(logger).updateOotbField("network_type", "unknown")
    }

    @Test
    fun network_type_access_network_state_granted_register_network_callback() {
        grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val mockedConnectivityManager = obtainMockedConnectivityManager(context)

        startNetworkAttributes(context)

        verify(mockedConnectivityManager).registerDefaultNetworkCallback(
            javaAny(ConnectivityManager.NetworkCallback::class.java),
        )
    }

    @Test
    fun radio_type_read_phone_state_granted() {
        grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = buildNetworkAttributes(context)

        verify(logger).updateOotbField("radio_type", "unknown")
    }

    @Test
    fun radio_type_read_phone_state_not_granted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = buildNetworkAttributes(context)

        verify(logger).updateOotbField("radio_type", "forbidden")
    }

    @Test
    fun invoke_whenWifiNetworkTransport_shouldMatchType() {
        val logger = invokeWithNetworkCapabilities(NetworkTransport.WIFI)

        verify(logger).updateOotbField("network_type", "wlan")
    }

    @Test
    fun invoke_whenCellularNetworkTransport_shouldMatchType() {
        val logger = invokeWithNetworkCapabilities(NetworkTransport.CELLULAR)

        verify(logger).updateOotbField("network_type", "wwan")
    }

    @Test
    fun invoke_whenEthernetNetworkTransport_shouldMatchType() {
        val logger = invokeWithNetworkCapabilities(NetworkTransport.ETHERNET)

        verify(logger).updateOotbField("network_type", "ethernet")
    }

    @Test
    fun invoke_whenOtherNetworkTransport_shouldMatchType() {
        val logger = invokeWithNetworkCapabilities(NetworkTransport.OTHER)

        verify(logger).updateOotbField("network_type", "other")
    }

    @Test
    fun invoke_whenLte_shouldSetLteRadioType() {
        grantPermissions(Manifest.permission.READ_PHONE_STATE)

        val logger =
            invokeWithNetworkCapabilities(
                radioType = TelephonyManager.NETWORK_TYPE_LTE,
            )

        verify(logger).updateOotbField("radio_type", "lte")
    }

    @Test
    fun invoke_whenGsm_shouldSetGsmRadioType() {
        grantPermissions(Manifest.permission.READ_PHONE_STATE)

        val logger =
            invokeWithNetworkCapabilities(
                radioType = TelephonyManager.NETWORK_TYPE_GSM,
            )

        verify(logger).updateOotbField("radio_type", "gsm")
    }

    @Test
    fun invoke_whenOnLost_shouldSetUnknown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger: IInternalLogger = mock()
        val networkAttributes = NetworkAttributes(context, MoreExecutors.newDirectExecutorService())
        val network = mock(Network::class.java)
        networkAttributes.start(logger)
        networkAttributes.onLost(network)

        verify(logger).updateOotbField("network_type", "unknown")
    }

    @Test
    fun start_forwards_network_changes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val networkAttributes = NetworkAttributes(context, MoreExecutors.newDirectExecutorService())
        val logger: IInternalLogger = mock()

        networkAttributes.start(logger)
        networkAttributes.onLost(mock(Network::class.java))

        verify(logger).updateOotbField("network_type", "unknown")
    }

    private fun invokeWithNetworkCapabilities(
        transport: NetworkTransport = NetworkTransport.WIFI,
        radioType: Int = TelephonyManager.NETWORK_TYPE_UNKNOWN,
    ): IInternalLogger {
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val networkAttributes = NetworkAttributes(context, MoreExecutors.newDirectExecutorService())
        val network = mock(Network::class.java)
        val capabilities = mock(NetworkCapabilities::class.java)
        val telephonyManager = mock(TelephonyManager::class.java)
        val logger: IInternalLogger = mock()

        `when`(capabilities.hasTransport(TRANSPORT_WIFI)).thenReturn(transport == NetworkTransport.WIFI)
        `when`(capabilities.hasTransport(TRANSPORT_CELLULAR)).thenReturn(transport == NetworkTransport.CELLULAR)
        `when`(capabilities.hasTransport(TRANSPORT_ETHERNET)).thenReturn(transport == NetworkTransport.ETHERNET)
        `when`(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager)
        @Suppress("DEPRECATION")
        `when`(telephonyManager.networkType).thenReturn(radioType)

        networkAttributes.start(logger)
        networkAttributes.onCapabilitiesChanged(network, capabilities)
        return logger
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

    private fun buildNetworkAttributes(context: Context): IInternalLogger {
        val networkAttributes = NetworkAttributes(context, MoreExecutors.newDirectExecutorService())
        val capabilities = NetworkCapabilities()
        val network = mock(Network::class.java)
        val logger: IInternalLogger = mock()
        networkAttributes.start(logger)
        networkAttributes.onCapabilitiesChanged(network, capabilities)
        return logger
    }

    private fun startNetworkAttributes(context: Context): IInternalLogger {
        val logger: IInternalLogger = mock()
        NetworkAttributes(context, MoreExecutors.newDirectExecutorService()).start(logger)
        return logger
    }

    private enum class NetworkTransport {
        WIFI,
        CELLULAR,
        ETHERNET,
        OTHER,
    }
}
