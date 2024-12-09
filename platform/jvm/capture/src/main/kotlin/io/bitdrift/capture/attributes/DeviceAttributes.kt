// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.attributes

import android.content.Context
import android.os.Build
import androidx.core.os.ConfigurationCompat
import io.bitdrift.capture.providers.FieldProvider
import io.bitdrift.capture.providers.Fields

internal class DeviceAttributes(private val context: Context) : FieldProvider {

    fun model(): String {
        return Build.MODEL
    }

    override fun invoke(): Fields {
        return mapOf(
            "model" to model(),
            "_locale" to ConfigurationCompat.getLocales(context.resources.configuration)[0].toString(),
        )
    }
}
