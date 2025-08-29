// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

class ReporterConfigCacheTests {
    @Test
    fun testReadValues() {
        val input = "key,true\nother,false\nmore.stuff,a bit of cheese"
        val values = ReporterConfigCache.readValues(input)
        assertThat(values).isNotNull
        assertThat(values!!["key"]).isEqualTo(true)
        assertThat(values["other"]).isEqualTo(false)
        assertThat(values["more.stuff"]).isEqualTo("a bit of cheese")
        assertThat(values["<not here>"]).isNull()
    }

    @Test
    fun testReadNonexistentFile() {
        val input = File("doesnotexist.txt")
        val values = ReporterConfigCache.readValues(input)
        assertThat(values).isNull()
    }
}
