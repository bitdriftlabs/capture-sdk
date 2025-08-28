// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.io.IOException

class ConfigCacheTests {
    @Test
    fun testReadValues() {
        val input = "key,true\nother,false\nmore.stuff,a bit of cheese"
        val result = ConfigCache.readValues(input)
        assertThat(result.isSuccess).isTrue

        val values = result.getOrNull()
        assertThat(values!!["key"]).isEqualTo(true)
        assertThat(values["other"]).isEqualTo(false)
        assertThat(values["more.stuff"]).isEqualTo("a bit of cheese")
        assertThat(values["<not here>"]).isNull()
    }

    @Test
    fun testReadValuesCorrupt() {
        // missing value on first line
        val input = "key\nother,false\nmore.stuff,a bit of cheese"
        val result = ConfigCache.readValues(input)
        assertThat(result.isFailure).isTrue
        assertThat(result.exceptionOrNull()).isInstanceOf(CacheFormattingError::class.java)
    }

    @Test
    fun testReadNonexistentFile() {
        val input = File("doesnotexist.txt")
        val result = ConfigCache.readValues(input)
        assertThat(result.isFailure).isTrue
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
    }
}
