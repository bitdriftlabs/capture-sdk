// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.data.model

import io.bitdrift.capture.LogLevel

/** Config feature state */
data class ConfigState(
    val apiKey: String = "",
    val apiUrl: String = "",
    val sessionStrategy: String = "",
    val isDeferredStart: Boolean = false,
    val selectedLogLevel: LogLevel = LogLevel.INFO,
)
