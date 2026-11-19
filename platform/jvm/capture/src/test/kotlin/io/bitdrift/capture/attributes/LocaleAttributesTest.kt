// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.attributes

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.providers.Field
import io.bitdrift.capture.providers.FieldValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class LocaleAttributesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun initial_ootb_fields_contains_locale() {
        val fields = LocaleAttributes(context).initialOotbFields()

        assertThat(fields).containsExactly(Field("_locale", FieldValue.StringField("en_US")))
    }

    @Test
    fun locale_changes_update_ootb_field() {
        val attributes = LocaleAttributes(context)
        val logger: IInternalLogger = mock()
        val frenchCanadianConfig = Configuration(context.resources.configuration).apply { setLocale(Locale.CANADA_FRENCH) }

        attributes.start(logger)
        verifyNoInteractions(logger)
        attributes.onConfigurationChanged(frenchCanadianConfig)
        attributes.onConfigurationChanged(frenchCanadianConfig)

        verify(logger, times(1)).updateOotbField("_locale", "fr_CA")
    }
}
