// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.attributes

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.utils.BuildTypeChecker
import java.util.Locale

internal class ClientAttributes(
    private val context: Context,
    private val processLifecycleOwner: LifecycleOwner,
) : IClientAttributes {
    private val resources = context.resources

    override val appId = context.packageName ?: UNKNOWN_FIELD_VALUE

    override val appVersion by lazy {
        packageInfo?.versionName ?: "?.?.?"
    }

    override val appVersionCode by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: -1
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: -1
        }
    }

    override val supportedAbis: List<String> by lazy { Build.SUPPORTED_ABIS.toList() }

    override val architecture = supportedAbis.firstOrNull() ?: UNKNOWN_FIELD_VALUE

    override val osVersion: String by lazy { Build.VERSION.RELEASE }

    override val osApiLevel: Int by lazy { Build.VERSION.SDK_INT }

    override val osBrand: String = Build.BRAND

    override val model: String = Build.MODEL

    override val locale: String
        get() = getCurrentLocale()?.toString() ?: UNKNOWN_FIELD_VALUE

    override val localeCountryCode: String?
        get() = getCurrentLocale()?.country?.takeIf { it.isNotEmpty() }

    override val manufacturer: String = Build.MANUFACTURER

    @Suppress("SwallowedException")
    private val packageInfo: PackageInfo? by lazy {
        try {
            context.packageManager.getPackageInfoCompat(appId)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns the OOTB snapshots needed before the logger can accept logs.
     *
     * Process lifecycle state can be read from the logger runtime thread. Subsequent lifecycle
     * events keep the foreground field current on the main thread.
     */
    internal fun initialOotbFields(): Array<Field> =
        arrayOf(
            Field(FOREGROUND_KEY, FieldValue.StringField(foregroundValue())),
        )

    private fun isForeground(): Boolean {
        // refer to lifecycle states https://developer.android.com/topic/libraries/architecture/lifecycle#lc
        val appState = processLifecycleOwner.lifecycle.currentState
        return if (appState.isAtLeast(Lifecycle.State.STARTED)) {
            // onStart call happened - app is in foreground
            true
        } else {
            // onStop call happened - app is in background
            false
        }
    }

    private fun foregroundValue(): String = if (isForeground()) "1" else "0"

    private fun PackageManager.getPackageInfoCompat(
        packageName: String,
        flags: Int = 0,
    ): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            getPackageInfo(packageName, flags)
        }

    /**
     * Gets the current locale.
     */
    private fun getCurrentLocale(configuration: Configuration = resources.configuration): Locale? =
        ConfigurationCompat.getLocales(configuration).get(0)

    /**
     * Returns the installation source (e.g. `com.android.vending` will be shown when
     * installed from the play store)
     */
    fun getInstallationSource(
        appContext: Context,
        errorHandler: ErrorHandler,
    ): String =
        runCatching {
            if (BuildTypeChecker.isDebuggable(appContext)) {
                return@runCatching DEBUG_BUILD_INSTALLATION_MESSAGE
            }
            val packageManager = appContext.packageManager
            val packageName = appContext.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
                    ?: UNKNOWN_FIELD_VALUE
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName) ?: UNKNOWN_FIELD_VALUE
            }
        }.getOrElse {
            errorHandler.handleError("Could not determine Installation source", it)
            UNKNOWN_FIELD_VALUE
        }

    /**
     * Holds constants for Client attributes
     */
    companion object {
        const val FOREGROUND_KEY = "foreground"

        // The unique sdk library that can be used for custom reports
        const val SDK_LIBRARY_ID = "io.bitdrift.capture-android"

        private const val UNKNOWN_FIELD_VALUE = "unknown"

        private const val DEBUG_BUILD_INSTALLATION_MESSAGE = "Debug build installation"
    }
}
