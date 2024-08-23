// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.attributes

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT
import android.telephony.TelephonyManager.NETWORK_TYPE_CDMA
import android.telephony.TelephonyManager.NETWORK_TYPE_EDGE
import android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD
import android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0
import android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A
import android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B
import android.telephony.TelephonyManager.NETWORK_TYPE_GPRS
import android.telephony.TelephonyManager.NETWORK_TYPE_GSM
import android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA
import android.telephony.TelephonyManager.NETWORK_TYPE_HSPA
import android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP
import android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA
import android.telephony.TelephonyManager.NETWORK_TYPE_IWLAN
import android.telephony.TelephonyManager.NETWORK_TYPE_LTE
import android.telephony.TelephonyManager.NETWORK_TYPE_NR
import android.telephony.TelephonyManager.NETWORK_TYPE_TD_SCDMA
import android.telephony.TelephonyManager.NETWORK_TYPE_UMTS
import android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN
import androidx.core.content.ContextCompat
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.Fields
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("MissingPermission")
internal class NetworkAttributes(private val context: Context) : FieldProvider, ConnectivityManager.NetworkCallback() {
    @SuppressLint("InlinedApi")
    private val radioTypeNameMap = hashMapOf(
        NETWORK_TYPE_1xRTT to "onExRtt",
        NETWORK_TYPE_CDMA to "cdma",
        NETWORK_TYPE_EDGE to "edge",
        NETWORK_TYPE_EHRPD to "ehrpd",
        NETWORK_TYPE_EVDO_0 to "evdo0",
        NETWORK_TYPE_EVDO_A to "evdoA",
        NETWORK_TYPE_EVDO_B to "evdoB",
        NETWORK_TYPE_GPRS to "gprs",
        NETWORK_TYPE_GSM to "gsm",
        NETWORK_TYPE_HSDPA to "hsdpa",
        NETWORK_TYPE_HSPA to "hspa",
        NETWORK_TYPE_HSPAP to "hspap",
        NETWORK_TYPE_HSUPA to "hsupa",
        NETWORK_TYPE_IWLAN to "iwlan",
        NETWORK_TYPE_LTE to "lte",
        NETWORK_TYPE_NR to "nr",
        NETWORK_TYPE_TD_SCDMA to "tdScdma",
        NETWORK_TYPE_UMTS to "umts",
        NETWORK_TYPE_UNKNOWN to "unknown",
    )
    private val currentNetworkType: AtomicReference<String> = AtomicReference("unknown")
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        monitorNetworkType()
    }

    override fun invoke(): Fields {
        return mapOf(
            "carrier" to telephonyManager.networkOperatorName,
            "network_type" to currentNetworkType.get(),
            "radio_type" to permissiveOperation({ radioType() }, READ_PHONE_STATE),
        )
    }

    @SuppressLint("NewApi")
    @Suppress("SwallowedException")
    private fun monitorNetworkType() {
        // TODO(snowp): Can we end up with this permission later on? Consider responding to permission
        //  changes and adding in the callback when available.
        if (ContextCompat.checkSelfPermission(context, ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                // TODO(murki): Figure out how to query this data on api level < 23
                connectivityManager.activeNetwork?.let { network ->
                    updateNetworkType(connectivityManager.getNetworkCapabilities(network))
                }

                // TODO(snowp): Use registerDefaultNetworkCallback once we have a min api level of 24+
                connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), this)
            } catch (e: Throwable) {
                // Issue with some versions of Android: https://issuetracker.google.com/issues/175055271
                // can sometime throw an exception: "package does not belong to 10006"
                // We'll also exercise this path when api level < 23
                updateNetworkType(NetworkCapabilities(null))
            }
        }
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        updateNetworkType(networkCapabilities)
    }

    private fun updateNetworkType(networkCapabilities: NetworkCapabilities?) {
        currentNetworkType.set(
            networkCapabilities?.run {
                when {
                    hasTransport(TRANSPORT_WIFI) -> "wlan"
                    hasTransport(TRANSPORT_CELLULAR) -> "wwan"
                    hasTransport(TRANSPORT_ETHERNET) -> "ethernet"
                    else -> "other"
                }
            } ?: "unknown",
        )
    }

    private fun radioType(): String {
        @Suppress("DEPRECATION")
        return radioTypeNameMap[telephonyManager.networkType] ?: "unknown"
    }

    private fun permissiveOperation(func: () -> String, permission: String): String {
        // We'll only attempt to get this value if permission has been granted
        // TODO: Use androidx.core.content.ContextCompat - fix adding dep to "androidx.core:core-ktx:1.8.0"
        return if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            "forbidden"
        } else {
            func()
        }
    }
}
