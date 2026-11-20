// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

package io.bitdrift.capture

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.bitdrift.capture.commands.CommandRegistry
import io.bitdrift.capture.events.performance.DiskUsageMonitor
import io.bitdrift.capture.providers.session.SessionConfiguration
import io.bitdrift.capture.providers.session.SessionStrategy
import io.bitdrift.capture.threading.CaptureDispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Verifies the SDK resolves every internal storage location (files dir, cache dir,
 * SharedPreferences, ...) from the Context exactly once, synchronously on the thread that starts
 * the SDK, and never again afterwards.
 *
 * Some apps override these Context getters to namespace storage per feature, and point the SDK at
 * a stable root only for the duration of `Capture.Logger.start()`. A later or off-thread read
 * would silently move SDK state into a namespaced directory that the app may delete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class StorageLocationResolutionTest {
    private lateinit var testServer: TestApiServer
    private lateinit var context: StorageAccessRecordingApplication

    @Before
    fun setUp() {
        ContextHolder().create(ApplicationProvider.getApplicationContext())
        // The SDK's real (per-dispatcher) background threads are intentionally kept, so that
        // off-thread storage reads are observable.
        CaptureJniLibrary.load()
        testServer = TestApiServer()

        context = StorageAccessRecordingApplication(ApplicationProvider.getApplicationContext())

        // Enable fatal issue reporting so the IssueReporter, which uses the temp directory,
        // initializes and processes prior reports.
        val reportsDir = File(context.realFilesDir(), "bitdrift_capture/reports/")
        reportsDir.mkdirs()
        File(reportsDir, "config.csv").writeText("crash_reporting.enabled,true")
    }

    @After
    fun teardown() {
        testServer.stop()
    }

    @Test
    fun storageLocationsAreOnlyResolvedDuringStartOnTheCallingThread() {
        // Mirrors the steps `Capture.Logger.start()` performs synchronously.
        val logger =
            LoggerImpl(
                apiKey = "test",
                commandRegistry = CommandRegistry(CaptureJniLibrary),
                commandDispatcher = CommandRegistry.defaultDispatcher(),
                apiUrl = testServer.url,
                customFieldGetters = emptyList(),
                dateProvider = null,
                sessionStrategy = SessionStrategy.Configuration(SessionConfiguration()),
                configuration = Configuration(),
                context = context,
                // `preferences` is intentionally left at its default, so the real
                // SharedPreferences-backed store resolves its file from `context`.
            )
        logger.initIssueReporter()
        context.startCompleted()

        try {
            val stream = testServer.awaitNextStream()
            stream.configureAggressiveUploads()

            logger.logInternal(LogType.NORMAL, LogLevel.INFO, blocking = true) { "after_start" }
            testServer.nextUploadedLog()
            logger.flush(blocking = true)
            drainBackgroundWork()
        } finally {
            CaptureJniLibrary.shutdown(logger.loggerId)
        }

        // Sanity check that the recording context is actually the one the SDK reads from.
        assertThat(context.accessesDuringStart().map { it.getter })
            .contains("getFilesDir", "getCacheDir", "getSharedPreferences")
        assertThat(context.violations()).isEmpty()
    }

    @Test
    fun diskUsageMonitorResolvesInternalStorageAtConstruction() {
        val monitor = DiskUsageMonitor(MockPreferences(), context)
        context.startCompleted()

        assertThat(monitor.getDiskUsage().isNotEmpty()).isTrue()

        assertThat(context.violations()).isEmpty()
    }

    /**
     * Waits for queued background work while also running main-looper tasks, since background
     * tasks may post to (and wait on) the main thread.
     */
    private fun drainBackgroundWork() {
        val pending = CaptureDispatchers.CommonBackground.executorService.submit {}
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!pending.isDone) {
            check(System.nanoTime() < deadline) { "Timed out draining background work" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
}

/**
 * An [Application] that records calls to the Context getters that resolve internal storage
 * locations. Calls made after [startCompleted], or on a thread other than the one that created
 * this instance, are reported as violations.
 *
 * External storage getters are not recorded: the SDK only reads them to measure disk usage.
 */
private class StorageAccessRecordingApplication(
    private val base: Context,
) : Application() {
    data class Access(
        val getter: String,
        val threadName: String,
        val afterStart: Boolean,
        val stackTrace: String,
    )

    private val startThread: Thread = Thread.currentThread()
    private val accesses = java.util.concurrent.CopyOnWriteArrayList<Access>()

    @Volatile
    private var started = false

    init {
        attachBaseContext(base)
    }

    fun startCompleted() {
        started = true
    }

    fun accessesDuringStart(): List<Access> = accesses.filter { !it.afterStart }

    fun violations(): List<Access> = accesses.filter { it.afterStart || it.threadName != startThread.name }

    fun realFilesDir(): File = base.filesDir

    // `SdkDirectory` resolves through `applicationContext`, like an app whose Application
    // subclass performs the namespacing.
    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = record("getFilesDir") { super.getFilesDir() }

    override fun getCacheDir(): File = record("getCacheDir") { super.getCacheDir() }

    override fun getDataDir(): File = record("getDataDir") { super.getDataDir() }

    override fun getNoBackupFilesDir(): File = record("getNoBackupFilesDir") { super.getNoBackupFilesDir() }

    override fun getCodeCacheDir(): File = record("getCodeCacheDir") { super.getCodeCacheDir() }

    override fun getDir(
        name: String?,
        mode: Int,
    ): File = record("getDir") { super.getDir(name, mode) }

    override fun getDatabasePath(name: String?): File = record("getDatabasePath") { super.getDatabasePath(name) }

    override fun getFileStreamPath(name: String?): File = record("getFileStreamPath") { super.getFileStreamPath(name) }

    override fun openFileInput(name: String?): FileInputStream = record("openFileInput") { super.openFileInput(name) }

    override fun openFileOutput(
        name: String?,
        mode: Int,
    ): FileOutputStream = record("openFileOutput") { super.openFileOutput(name, mode) }

    override fun getSharedPreferences(
        name: String?,
        mode: Int,
    ): SharedPreferences = record("getSharedPreferences") { super.getSharedPreferences(name, mode) }

    private fun <T> record(
        getter: String,
        block: () -> T,
    ): T {
        val thread = Thread.currentThread()
        accesses.add(
            Access(
                getter = getter,
                threadName = thread.name,
                afterStart = started,
                stackTrace = thread.stackTrace.drop(2).joinToString("\n    "),
            ),
        )
        return block()
    }
}
