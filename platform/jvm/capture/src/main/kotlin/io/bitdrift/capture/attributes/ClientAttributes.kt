// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.attributes

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.bitdrift.capture.ErrorHandler
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.Fields
import io.bitdrift.capture.utils.BuildTypeChecker

internal class ClientAttributes(
    context: Context,
    private val processLifecycleOwner: LifecycleOwner,
) : IClientAttributes,
    FieldProvider {
    private val resources = context.resources
    private var cachedForegroundState: ForegroundState? = null
    private var cachedConfiguration: Configuration = Configuration(resources.configuration)

    private var cachedLocale: String? = null

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

    override val locale: String by lazy { getCurrentLocale() }

    override val manufacturer: String = Build.MANUFACTURER

    @Suppress("SwallowedException")
    private val packageInfo: PackageInfo? by lazy {
        try {
            context.packageManager.getPackageInfoCompat(appId)
        } catch (e: Exception) {
            null
        }
    }

    private val cachedAttributes by lazy {
        mutableMapOf(
            // The package name which identifies the running app (e.g. me.foobar.android).
            "app_id" to appId,
            // Operating system. Always Android for this code path.
            "os" to "Android",
            // The operating system version (e.g. 12.1).
            "os_version" to osVersion,
            // Device model name.
            "model" to model,
            // The SDK level (e.g. 35).
            "_os_api_level" to osApiLevel.toString(),
            // The version of this package, as specified by the manifest's `versionName` attribute.
            // (e.g. 1.2.33).
            "app_version" to appVersion,
            // A positive integer used as an internal version number.
            // This number helps determine whether one version is more recent than another.
            "_app_version_code" to appVersionCode.toString(),
            // The current architecture e.g. (arm64-v8a).
            "_architecture" to architecture,
        )
    }

    override fun invoke(): Fields {
        updateForegroundState()
        updateLocaleIfNeeded()
        return cachedAttributes
    }

    private fun updateForegroundState() {
        val currentState = if (isForeground()) ForegroundState.Foreground else ForegroundState.Background

        if (cachedForegroundState != currentState) {
            cachedForegroundState = currentState
            cachedAttributes["foreground"] = currentState.value
        }
    }

    private fun updateLocaleIfNeeded() {
        val currentConfig = resources.configuration
        val configDiff = currentConfig.diff(cachedConfiguration)

        if (cachedLocale == null || (configDiff and ActivityInfo.CONFIG_LOCALE == ActivityInfo.CONFIG_LOCALE)) {
            val updatedLocale = getCurrentLocale()

            if (cachedLocale != updatedLocale) {
                cachedLocale = updatedLocale
                cachedAttributes["_locale"] = updatedLocale
            }
            cachedConfiguration = Configuration(currentConfig)
        }
    }

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
     * Gets the current locale string.
     */
    private fun getCurrentLocale(): String {
        val locales = ConfigurationCompat.getLocales(resources.configuration)
        return if (!locales.isEmpty) {
            locales[0].toString()
        } else {
            UNKNOWN_FIELD_VALUE
        }
    }

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

    private sealed interface ForegroundState {
        val value: String

        object Foreground : ForegroundState {
            override val value: String = "1"
        }

        object Background : ForegroundState {
            override val value: String = "0"
        }
    }

    /**
     * Holds constants for Client attributes
     */
    companion object {
        // The unique sdk library that can be used for custom reports
        // TODO(FranAguilera): BIT-5149 Finalize model
        const val SDK_LIBRARY_ID = "io.bitdrift.capture-android"

        private const val UNKNOWN_FIELD_VALUE = "unknown"

        private const val DEBUG_BUILD_INSTALLATION_MESSAGE = "Debug build installation"
    }
}
