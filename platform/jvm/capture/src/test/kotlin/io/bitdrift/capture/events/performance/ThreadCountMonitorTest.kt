// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.utils.toStringMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ThreadCountMonitorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val runtime: Runtime = mock()

    @Test
    fun getThreadCount_withTaskEntries_reportsEntryCount() {
        val taskDir = tempFolder.newFolder("task")
        repeat(3) { File(taskDir, "$it").mkdir() }
        val monitor = ThreadCountMonitor(taskDir)

        val fields = monitor.getThreadCount()

        assertThat(fields.toStringMap()).isEqualTo(mapOf("_thread_count" to "3"))
    }

    @Test
    fun getThreadCount_whenRuntimeFeatureEnabled_reportsEntryCount() {
        val taskDir = tempFolder.newFolder("task")
        File(taskDir, "1").mkdir()
        whenever(runtime.isEnabled(RuntimeFeature.THREAD_COUNT_FIELDS)).thenReturn(true)
        val monitor = ThreadCountMonitor(taskDir).apply { runtime = this@ThreadCountMonitorTest.runtime }

        val fields = monitor.getThreadCount()

        assertThat(fields.toStringMap()).isEqualTo(mapOf("_thread_count" to "1"))
    }

    @Test
    fun getThreadCount_whenRuntimeFeatureDisabled_returnsEmpty() {
        val taskDir = tempFolder.newFolder("task")
        File(taskDir, "1").mkdir()
        whenever(runtime.isEnabled(RuntimeFeature.THREAD_COUNT_FIELDS)).thenReturn(false)
        val monitor = ThreadCountMonitor(taskDir).apply { runtime = this@ThreadCountMonitorTest.runtime }

        val fields = monitor.getThreadCount()

        assertThat(fields).isEqualTo(ArrayFields.EMPTY)
    }

    @Test
    fun getThreadCount_whenTaskDirMissing_returnsEmpty() {
        val monitor = ThreadCountMonitor(File(tempFolder.root, "missing"))

        val fields = monitor.getThreadCount()

        assertThat(fields).isEqualTo(ArrayFields.EMPTY)
    }

    @Test
    fun getThreadCount_whenListingThrows_returnsEmpty() {
        val taskDir: File = mock()
        whenever(taskDir.list()).thenThrow(SecurityException("denied"))
        val monitor = ThreadCountMonitor(taskDir)

        val fields = monitor.getThreadCount()

        assertThat(fields).isEqualTo(ArrayFields.EMPTY)
    }
}
