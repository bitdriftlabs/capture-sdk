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
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.Fields

internal class ClientAttributes(
    context: Context,
    private val processLifecycleOwner: LifecycleOwner,
) : FieldProvider {
    val appId = context.packageName ?: "unknown"

    val appVersion: String
        get() {
            return packageInfo?.versionName ?: "?.?.?"
        }

    val appVersionCode: Long
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: -1
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: -1
            }
        }

    @Suppress("SwallowedException")
    private val packageInfo: PackageInfo? =
        try {
            context.packageManager.getPackageInfoCompat(appId)
        } catch (e: Exception) {
            null
        }

    override fun invoke(): Fields =
        mapOf(
            // The package name which identifies the running app (e.g. me.foobar.android)
            "app_id" to appId,
            // Operating system. Always Android for this code path.
            "os" to "Android",
            // The operating system version (e.g. 12.1)
            "os_version" to Build.VERSION.RELEASE,
            // Whether or not the app was in the background by the time the log was fired.
            "foreground" to isForeground(),
            // The version of this package, as specified by the manifest's `versionName` attribute
            // (e.g. 1.2.33).
            "app_version" to appVersion,
            // A positive integer used as an internal version number.
            // This number helps determine whether one version is more recent than another.
            "_app_version_code" to appVersionCode.toString(),
        )

    private fun isForeground(): String {
        // refer to lifecycle states https://developer.android.com/topic/libraries/architecture/lifecycle#lc
        val appState = processLifecycleOwner.lifecycle.currentState
        return if (appState.isAtLeast(Lifecycle.State.STARTED)) {
            // onStart call happened - app is in foreground
            "1"
        } else {
            // onStop call happened - app is in background
            "0"
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
     * Holds constants for Client attributes
     */
    companion object {
        // The unique sdk library that can be used for custom reports
        const val SDK_LIBRARY_ID = "io.bitdrift.capture-android"
    }
}
