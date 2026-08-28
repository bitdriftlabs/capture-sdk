// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.attributes

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.core.os.ConfigurationCompat
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.FieldValue

/** Provides the locale snapshot and forwards configuration changes to the native OOTB field store. */
internal class LocaleAttributes(
    private val context: Context,
) : ComponentCallbacks {
    @Volatile
    private var locale = currentLocale()

    @Volatile
    private var logger: IInternalLogger? = null

    fun initialOotbFields(): Array<Field> = arrayOf(Field(LOCALE_KEY, FieldValue.StringField(locale)))

    /**
     * Starts forwarding locale changes.
     *
     * The initial locale is installed synchronously when the logger is created, so starting this
     * listener does not need to replay it.
     */
    fun start(logger: IInternalLogger) {
        this.logger = logger
        context.registerComponentCallbacks(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val updatedLocale = currentLocale(newConfig)
        if (locale != updatedLocale) {
            locale = updatedLocale
            logger?.updateOotbField(LOCALE_KEY, updatedLocale)
        }
    }

    override fun onLowMemory() = Unit

    private fun currentLocale(configuration: Configuration = context.resources.configuration): String =
        ConfigurationCompat.getLocales(configuration).get(0)?.toString() ?: UNKNOWN_FIELD_VALUE

    private companion object {
        const val LOCALE_KEY = "_locale"
        const val UNKNOWN_FIELD_VALUE = "unknown"
    }
}
