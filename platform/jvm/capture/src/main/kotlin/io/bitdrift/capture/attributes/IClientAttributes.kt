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

    /** The operating system version (e.g. 12.1). */
    val osVersion: String

    /** A list of the currently supported ABIs. */
    val supportedAbis: List<String>

    /** The current architecture (e.g. arm64-v8a). */
    val architecture: String
}
