// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
package io.bitdrift.capture.attributes

/**
 * Provides relevant client attributes
 */
interface IClientAttributes {
    /** The package name which identifies the running app (e.g. me.foobar.android). */
    val appId: String

    /** The version of this package, as specified by the manifest's `versionName` attribute (e.g. 1.2.33). */
    val appVersion: String

    /** A positive integer used as an internal version number. This helps determine version recency. */
    val appVersionCode: Long

    /** Consumer-facing operating system brand name. */
    val osBrand: String

    /**
     * The operating system version (e.g. 12.1, 16, 16 Beta, etc).
     *
     * This corresponds to android.os.Build.VERSION.RELEASE.
     */
    val osVersion: String

    /**
     * The API level of the operating system currently running on the device
     * (for example, 31 for Android 12, 35 for Android 15).
     *
     * This corresponds to android.os.Build.VERSION.SDK_INT.
     */
    val osApiLevel: Int

    /** A list of the currently supported ABIs. */
    val supportedAbis: List<String>

    /** The current architecture (e.g. arm64-v8a). */
    val architecture: String

    /** Device manufacturer name. */
    val manufacturer: String

    /** Device model name. */
    val model: String

    /** Current Locale (e.g. en-US). */
    val locale: String
}
