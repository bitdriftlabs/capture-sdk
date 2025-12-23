// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.model

/** Session feature state */
data class SessionState(
    val isSdkInitialized: Boolean = false,
    val sessionId: String? = null,
    val sessionUrl: String? = null,
    val deviceCode: String? = null,
    val deviceId: String? = null,
    val isDeviceCodeValid: Boolean = false,
    val deviceCodeError: String? = null,
)
