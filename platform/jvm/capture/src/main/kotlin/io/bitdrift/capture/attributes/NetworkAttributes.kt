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
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.NETWORK_TYPE_EDGE
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
import io.bitdrift.capture.threading.CaptureDispatchers
import java.util.concurrent.ExecutorService

@SuppressLint("MissingPermission")
internal class NetworkAttributes(
    private val context: Context,
    executor: ExecutorService = CaptureDispatchers.CommonBackground.executorService,
) : ConnectivityManager.NetworkCallback(),
    FieldProvider {
    @SuppressLint("InlinedApi")
    private val radioTypeNameMap =
        hashMapOf(
            NETWORK_TYPE_EDGE to "edge",
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

    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Volatile
    private var currentFields: Map<String, String> = emptyMap()

    init {
        executor.execute {
            monitorNetworkType()
        }
    }

    override fun invoke(): Fields = currentFields

    @SuppressLint("NewApi")
    @Suppress("SwallowedException")
    private fun monitorNetworkType() {
        if (ContextCompat.checkSelfPermission(context, ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                connectivityManager.activeNetwork?.let { network ->
                    updateNetworkType(connectivityManager.getNetworkCapabilities(network))
                }
                connectivityManager.registerDefaultNetworkCallback(this)
            } catch (e: Throwable) {
                // Issue with some versions of Android: https://issuetracker.google.com/issues/175055271
                updateNetworkType(NetworkCapabilities(null))
            }
        }
    }

    override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities,
    ) {
        updateNetworkType(networkCapabilities)
        updateTelephonyAttributes()
    }

    private fun updateNetworkType(networkCapabilities: NetworkCapabilities?) {
        val type =
            networkCapabilities?.run {
                when {
                    hasTransport(TRANSPORT_WIFI) -> "wlan"
                    hasTransport(TRANSPORT_CELLULAR) -> "wwan"
                    hasTransport(TRANSPORT_ETHERNET) -> "ethernet"
                    else -> "other"
                }
            } ?: "unknown"

        updateFields(networkType = type)
    }

    private fun updateTelephonyAttributes() {
        val carrier = telephonyManager.networkOperatorName ?: "unknown"
        val radioType = permissiveOperation({ radioType() }, READ_PHONE_STATE)

        updateFields(carrier = carrier, radioType = radioType)
    }

    private fun updateFields(
        carrier: String? = null,
        networkType: String? = null,
        radioType: String? = null,
    ) {
        val updated =
            currentFields.toMutableMap().apply {
                carrier?.let { this[KEY_CARRIER] = it }
                networkType?.let { this[KEY_NETWORK_TYPE] = it }
                radioType?.let { this[KEY_RADIO_TYPE] = it }
            }
        currentFields = updated.toMap()
    }

    private fun radioType(): String {
        @Suppress("DEPRECATION")
        return radioTypeNameMap[telephonyManager.networkType] ?: "unknown"
    }

    private fun permissiveOperation(
        func: () -> String,
        permission: String,
    ): String {
        // We'll only attempt to get this value if permission has been granted
        // TODO: Use androidx.core.content.ContextCompat - fix adding dep to "androidx.core:core-ktx:1.8.0"
        return if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            "forbidden"
        } else {
            func()
        }
    }

    private companion object {
        private const val KEY_CARRIER = "carrier"
        private const val KEY_NETWORK_TYPE = "network_type"
        private const val KEY_RADIO_TYPE = "radio_type"
    }
}
