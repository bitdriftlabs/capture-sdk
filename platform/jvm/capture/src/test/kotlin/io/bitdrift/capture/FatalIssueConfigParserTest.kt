// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.ContextHolder.Companion.APP_CONTEXT
import io.bitdrift.capture.reports.parser.FatalIssueConfigParser.getFatalIssueConfigDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class FatalIssueConfigParserTest {
    @Before
    fun setup() {
        val initializer = ContextHolder()
        initializer.create(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun getFatalIssueConfigDetails_withCachedDirPlaceHolder_shouldReturnExpectedPath() {
        val configurationPath = "{cache_dir}/acme,json"

        val details = getFatalIssueConfigDetails(APP_CONTEXT, configurationPath)

        assertThat(details?.sourceDirectory?.absolutePath).isEqualTo(APP_CONTEXT.cacheDir.absolutePath + "/acme")
        assertThat(details?.extensionFileName).isEqualTo("json")
    }

    @Test
    fun getFatalIssueConfigDetails_withFilesDirPlaceHolder_shouldReturnExpectedPath() {
        val configurationPath = "{files_dir}/acme,json"

        val details = getFatalIssueConfigDetails(APP_CONTEXT, configurationPath)

        assertThat(details?.sourceDirectory?.absolutePath).isEqualTo(APP_CONTEXT.filesDir.absolutePath + "/acme")
        assertThat(details?.extensionFileName).isEqualTo("json")
    }

    @Test
    fun getFatalIssueConfigDetails_withDataDirPlaceHolder_shouldReturnExpectedPath() {
        val configurationPath = "{data_dir}/acme,json"

        val details = getFatalIssueConfigDetails(APP_CONTEXT, configurationPath)

        assertThat(details?.sourceDirectory?.absolutePath).isEqualTo(APP_CONTEXT.applicationInfo.dataDir + "/acme")
        assertThat(details?.extensionFileName).isEqualTo("json")
    }

    @Test
    fun getFatalIssueConfigDetails_withoutPlaceHolder_shouldReturnExpectedPath() {
        val configurationPath = "/my fake path/acme,json"

        val details = getFatalIssueConfigDetails(APP_CONTEXT, configurationPath)

        assertThat(details?.sourceDirectory?.absolutePath).isEqualTo("/my fake path/acme")
        assertThat(details?.extensionFileName).isEqualTo("json")
    }

    @Test
    fun getFatalIssueConfigDetails_withExtraSpaces_shouldReturnExpectedPath() {
        val configurationPath = "  /my fake path/acme  ,     json      "

        val details = getFatalIssueConfigDetails(APP_CONTEXT, configurationPath)

        assertThat(details?.sourceDirectory?.absolutePath).isEqualTo("/my fake path/acme")
        assertThat(details?.extensionFileName).isEqualTo("json")
    }

    @Test
    fun getFatalIssueConfigDetails_withUnexpectedFormat_shouldReturnExpectedPath() {
        val configurationPath = "wrong_config"

        val details = getFatalIssueConfigDetails(APP_CONTEXT, configurationPath)

        assertThat(details).isNull()
    }
}
