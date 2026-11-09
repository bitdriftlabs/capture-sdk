// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.File
import java.io.IOException

class ConfigCacheTest {
    @Test
    fun testReadValues() {
        val input = "key,true\nother,false\nmore.stuff,a bit of cheese"
        val values = ConfigCache.readValues(input)

        assertThat(values["key"]).isEqualTo(true)
        assertThat(values["other"]).isEqualTo(false)
        assertThat(values["more.stuff"]).isEqualTo("a bit of cheese")
        assertThat(values["<not here>"]).isNull()
    }

    @Test
    fun testReadValuesCorrupt() {
        assertThatThrownBy {
            // missing value on first line
            ConfigCache.readValues("key\nother,false\nmore.stuff,a bit of cheese")
        }.isInstanceOf(CacheFormattingError::class.java)
    }

    @Test
    fun testReadNonexistentFile() {
        assertThatThrownBy {
            ConfigCache.readValues(File("doesnotexist.txt"))
        }.isInstanceOf(IOException::class.java)
    }
}
