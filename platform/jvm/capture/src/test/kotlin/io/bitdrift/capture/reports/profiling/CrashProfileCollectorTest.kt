// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.profiling

import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.bitdrift.capture.CaptureResult
import io.bitdrift.capture.Configuration
import io.bitdrift.capture.IPreferences
import io.bitdrift.capture.MockPreferences
import io.bitdrift.capture.common.Runtime
import io.bitdrift.capture.common.RuntimeConfig
import io.bitdrift.capture.common.RuntimeFeature
import io.bitdrift.capture.events.SafeEventListenerLogger
import io.bitdrift.capture.reports.profiling.CrashProfileCollector.Companion.ANR
import io.bitdrift.capture.reports.profiling.CrashProfileCollector.Companion.OOM
import io.bitdrift.capture.reports.profiling.CrashProfileStore.CrashProfileFileOperations
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashProfileCollectorTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val profiler = FakeProfiler()
    private val runtime: Runtime = mock()
    private val preferences = MockPreferences()
    private val submitted = mutableListOf<Triple<String, String, Boolean>>()
    private val encoded = mutableListOf<CrashProfileFacts>()
    private val store: CrashProfileStore = mock()
    private val pending = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() {
        CrashProfileSource.resetForTest()
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 3)
        whenever(runtime.getConfigValue(any<RuntimeConfig>())).thenAnswer { invocation ->
            invocation.getArgument<RuntimeConfig>(0).defaultValue
        }
        whenever(store.pendingResults()).thenAnswer { pending.toList() }
        whenever(store.adopt(any(), any())).thenAnswer { invocation ->
            val file = invocation.getArgument<File>(0)
            val kind = invocation.getArgument<CrashProfileKind>(1)
            if (profiler.pending.any { it.second == file.path }) {
                pending += kind.value to file.path
                profiler.pending.removeAll { it.second == file.path }
            }
            file
        }
        whenever(store.importProfile(any(), any())).thenReturn(File("/sdk-private/imported.anr"))
    }

    @After
    fun tearDown() {
        CrashProfileSource.resetForTest()
    }

    @Test
    fun disabledFlagsLeaveUnclaimedTriggersAlone() {
        profiler.triggerKinds += setOf(ANR, OOM)
        collector().start()

        assertThat(profiler.events).containsExactly("unregister")
        assertThat(profiler.triggerKinds).containsExactlyInAnyOrder(ANR, OOM)
        assertThat(CrashProfileSource.oomSource).isNull()
    }

    @Test
    fun startupOptOutOverridesEnabledRuntimeFlagsWithoutTouchingHostState() {
        enable(anr = true, oom = true)
        profiler.triggerKinds += setOf(ANR, OOM)
        profiler.pending += ANR to "/previous-anr"

        collector(configuration = Configuration()).start()
        profiler.deliver(ANR, "/host-anr")

        assertThat(profiler.events).containsExactly("unregister")
        assertThat(profiler.triggerKinds).containsExactlyInAnyOrder(ANR, OOM)
        assertThat(profiler.summaries).isEmpty()
        assertThat(profiler.pendingReads).isZero()
        assertThat(encoded).isEmpty()
        assertThat(submitted).isEmpty()
        assertThat(CrashProfileSource.oomSource).isNull()
    }

    @Test
    fun fatalIssueReportingOptOutOverridesProfilingOptIn() {
        enable(anr = true, oom = true)

        collector(
            configuration = Configuration(crashProfilingMode = CrashProfilingMode.AUTOMATIC, enableFatalIssueReporting = false),
        ).start()

        assertThat(profiler.events).containsExactly("unregister")
        assertThat(encoded).isEmpty()
        assertThat(CrashProfileSource.oomSource).isNull()
    }

    @Test
    fun enabledFlagsRecordSourceFactsBeforeOwningTriggersAndSubmitOnlyOwnedResults() {
        enable(anr = true, oom = true)
        val collector = SafeEventListenerLogger(collector())

        collector.start()
        collector.start()

        assertThat(profiler.events.take(3)).containsExactly("summary", "add=[anr, oom]", "register")
        // The real lifecycle wrapper suppresses duplicate starts and source preparation.
        val facts = encoded.single()
        assertThat(facts.pid).isEqualTo(Process.myPid())
        assertThat(facts.bootCount).isEqualTo(3)
        val source = encode(facts)
        assertThat(profiler.summaries).hasSize(1).allSatisfy { assertThat(it).isEqualTo(source) }
        assertThat(CrashProfileSource.oomSource).isEqualTo(source)

        profiler.deliver(ANR, "/anr")
        profiler.deliver(OOM, "/oom")
        profiler.deliver(null, "/direct")
        profiler.deliver(ANR, null)
        assertThat(submitted).containsExactly(Triple("/anr", ANR, true), Triple("/oom", OOM, true))
    }

    @Test
    fun earlierProcessResultsForOwnedKindsAreResubmittedOnceWithoutRetention() {
        enable(anr = true, oom = false)
        pending += listOf(ANR to "/previous-anr", OOM to "/previous-oom")
        val collector = SafeEventListenerLogger(collector())

        collector.start()
        collector.start()

        assertThat(submitted).containsExactly(Triple("/previous-anr", ANR, false))
    }

    @Test
    fun withoutOomSupportOnlyAnrIsOwned() {
        enable(anr = true, oom = true)

        collector(supportsOom = false).start()

        assertThat(profiler.events).contains("add=[anr]")
        assertThat(CrashProfileSource.oomSource).isNull()
    }

    @Test
    fun oomOnlyDoesNotOwnTheProcessStateSummary() {
        enable(anr = false, oom = true)

        collector().start()

        assertThat(profiler.summaries).isEmpty()
        assertThat(profiler.events).containsExactly("add=[oom]", "register")
        assertThat(CrashProfileSource.oomSource).isEqualTo(encode(encoded.single()))
    }

    @Test
    fun perKindFlagDisableTakesEffectAtNextSafeListenerStart() {
        enable(anr = true, oom = false)
        val executor = QueuedExecutor()
        val listener = SafeEventListenerLogger(collector(executor = executor))
        listener.start()
        executor.drain()

        enable(anr = false, oom = false)
        listener.start()
        executor.drain()

        // Per-kind flags do not restart the generic platform-events listener.
        assertThat(profiler.triggerKinds).containsExactly(ANR)
        // Kotlin still forwards results; native attribution independently checks the live flag.
        profiler.deliver(ANR, "/before-restart")
        assertThat(submitted).containsExactly(Triple("/before-restart", ANR, true))
        submitted.clear()
        listener.stop()
        listener.start()
        executor.drain()
        profiler.deliver(ANR, "/late")

        assertThat(profiler.events.takeLast(2)).containsExactly("remove=[anr]", "unregister")
        assertThat(submitted).isEmpty()
    }

    @Test
    fun startupOptOutRemovesPreviouslyClaimedTriggersAcrossRestartButPreservesOtherKinds() {
        enable(anr = true, oom = false)
        profiler.triggerKinds += OOM
        val first = collector()
        first.start()
        first.stop()
        profiler.events.clear()
        CrashProfileSource.resetForTest()
        encoded.clear()

        collector(configuration = Configuration()).start()
        collector(configuration = Configuration()).start()

        assertThat(profiler.events).containsExactly("remove=[anr]", "unregister", "unregister")
        assertThat(profiler.triggerKinds).containsExactly(OOM)
        assertThat(encoded).isEmpty()
        assertThat(CrashProfileSource.oomSource).isNull()
    }

    @Test
    fun disablingFatalIssueReportingAlsoRemovesPreviouslyClaimedTriggers() {
        enable(anr = true, oom = true)
        val first = collector()
        first.start()
        first.stop()
        profiler.events.clear()

        collector(
            configuration = Configuration(crashProfilingMode = CrashProfilingMode.AUTOMATIC, enableFatalIssueReporting = false),
        ).start()

        assertThat(profiler.events).containsExactly("remove=[anr, oom]", "unregister")
        assertThat(profiler.triggerKinds).isEmpty()
        assertThat(CrashProfileSource.oomSource).isNull()
    }

    @Test
    fun missingSourceFactsDoNotClaimOrRemoveHostTriggers() {
        enable(anr = true, oom = true)
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        profiler.triggerKinds += setOf(ANR, OOM)

        collector().start()

        assertThat(profiler.events).containsExactly("unregister")
        assertThat(profiler.triggerKinds).containsExactlyInAnyOrder(ANR, OOM)
        assertThat(encoded).isEmpty()
    }

    @Test
    fun failedRemovalRetainsOwnershipForTheNextAttempt() {
        enable(anr = true, oom = false)
        val first = collector()
        first.start()
        first.stop()
        profiler.events.clear()
        profiler.failure = IllegalStateException("profiling service unavailable")

        collector(configuration = Configuration()).start()
        assertThat(profiler.triggerKinds).containsExactly(ANR)

        profiler.failure = null
        collector(configuration = Configuration()).start()

        assertThat(profiler.events).containsExactly("remove=[anr]", "unregister")
        assertThat(profiler.triggerKinds).isEmpty()
    }

    @Test
    fun registrationFailureStillLeavesOwnershipForRestartCleanup() {
        enable(anr = false, oom = true)
        profiler.failure = IllegalStateException("profiling service unavailable")

        collector().start()

        profiler.failure = null
        collector(configuration = Configuration()).start()

        assertThat(profiler.events).containsExactly("remove=[oom]", "unregister")
    }

    @Test
    fun failedOwnershipClearsAreRetriedWithoutRemovingHandedOffTriggersAgain() {
        val durable = mutableMapOf("crash_profiles.owned.anr" to ANR, "crash_profiles.owned.oom" to OOM)
        durable.forEach { (key, value) -> preferences.setStringBlocking(key, value) }
        profiler.triggerKinds += setOf(ANR, OOM)
        var committed = false
        var attempts = 0
        val storage =
            object : IPreferences by preferences {
                override fun setStringBlocking(
                    key: String,
                    value: String?,
                ): Boolean {
                    preferences.setStringBlocking(key, value)
                    attempts++
                    if (!committed) return false
                    if (value == null) durable.remove(key) else durable[key] = value
                    return true
                }
            }
        val listener = SafeEventListenerLogger(collector(configuration = manualConfiguration(), profilePreferences = storage))
        listener.start()
        assertThat(preferences.getString("crash_profiles.owned.anr")).isNull()
        assertThat(durable).hasSize(2)
        assertThat(profiler.triggerKinds).isEmpty()

        // The application registers after the first removal. Retrying disk cleanup must not
        // remove either type again, including the marker not reached before the first failure.
        profiler.triggerKinds += setOf(ANR, OOM)
        listener.stop()
        listener.start()
        assertThat(attempts).isEqualTo(2)
        assertThat(profiler.triggerKinds).containsExactlyInAnyOrder(ANR, OOM)

        committed = true
        listener.stop()
        listener.start()

        assertThat(attempts).isEqualTo(4)
        assertThat(durable).isEmpty()
        assertThat(preferences.getString("crash_profiles.owned.anr")).isNull()
        assertThat(preferences.getString("crash_profiles.owned.oom")).isNull()
        assertThat(profiler.events.filter { it.startsWith("remove=") }).containsExactly("remove=[anr, oom]")
        val restartedPreferences = MockPreferences()
        durable.forEach { (key, value) -> restartedPreferences.setStringBlocking(key, value) }
        collector(configuration = manualConfiguration(), profilePreferences = restartedPreferences).start()
        assertThat(profiler.triggerKinds).containsExactlyInAnyOrder(ANR, OOM)
    }

    @Test
    fun reclaimingATriggerCancelsItsFailedOwnershipClear() {
        preferences.setStringBlocking("crash_profiles.owned.anr", ANR)
        profiler.triggerKinds += ANR
        var committed = false
        val writes = mutableListOf<String?>()
        val storage =
            object : IPreferences by preferences {
                override fun setStringBlocking(
                    key: String,
                    value: String?,
                ): Boolean {
                    writes += value
                    preferences.setStringBlocking(key, value)
                    return committed
                }
            }
        val listener = SafeEventListenerLogger(collector(profilePreferences = storage))
        listener.start()
        assertThat(profiler.triggerKinds).isEmpty()

        listener.stop()
        committed = true
        enable(anr = true, oom = false)
        listener.start()

        assertThat(writes).containsExactly(null, ANR)
        assertThat(preferences.getString("crash_profiles.owned.anr")).isEqualTo(ANR)
        assertThat(profiler.triggerKinds).containsExactly(ANR)
    }

    @Test
    fun failedOwnershipCommitsAreRetriedEvenAfterInMemoryPreferencesChange() {
        enable(anr = true, oom = false)
        var committed = false
        var attempts = 0
        val storage =
            object : IPreferences by preferences {
                override fun setStringBlocking(
                    key: String,
                    value: String?,
                ): Boolean {
                    preferences.setStringBlocking(key, value)
                    attempts++
                    return committed
                }
            }
        val listener = SafeEventListenerLogger(collector(profilePreferences = storage))
        repeat(2) {
            listener.start()
            assertThat(preferences.getString("crash_profiles.owned.anr")).isEqualTo(ANR)
            assertThat(profiler.triggerKinds).isEmpty()
            assertThat(profiler.events).doesNotContain("register")
            listener.stop()
        }
        assertThat(attempts).isEqualTo(2)

        committed = true
        listener.start()
        profiler.deliver(ANR, "/live")

        assertThat(attempts).isEqualTo(3)
        assertThat(profiler.triggerKinds).containsExactly(ANR)
        assertThat(submitted).containsExactly(Triple("/live", ANR, true))
    }

    @Test
    fun legacyEnumerationFailureDoesNotPreventListenerRegistration() {
        enable(anr = true, oom = false)
        profiler.pendingFailure = IllegalStateException("profiling service unavailable")

        SafeEventListenerLogger(collector()).start()
        profiler.deliver(ANR, "/live")

        assertThat(profiler.events).contains("register")
        assertThat(submitted).containsExactly(Triple("/live", ANR, true))
    }

    @Test
    fun pendingScanFailureDoesNotPreventListenerRegistration() {
        enable(anr = true, oom = false)
        whenever(store.pendingResults()).thenAnswer { throw IOException("retention rename during scan") }

        SafeEventListenerLogger(collector()).start()
        profiler.deliver(ANR, "/live")

        assertThat(profiler.events).contains("register")
        assertThat(submitted).containsExactly(Triple("/live", ANR, true))
    }

    @Test
    fun pendingSubmissionFailureDoesNotPreventListenerRegistration() {
        enable(anr = true, oom = false)
        pending += ANR to "/pending"
        val listener =
            collector(
                submit = { path, kind, retain ->
                    if (path == "/pending") throw IllegalStateException("native attribution unavailable")
                    submitted += Triple(path, kind, retain)
                },
            )

        SafeEventListenerLogger(listener).start()
        profiler.deliver(ANR, "/live")

        assertThat(profiler.events).contains("register")
        assertThat(submitted).containsExactly(Triple("/live", ANR, true))
    }

    @Test
    fun stopUnregistersTheListener() {
        enable(anr = true, oom = false)
        val collector = collector()
        collector.start()

        collector.stop()

        assertThat(profiler.events.last()).isEqualTo("unregister")
    }

    @Test
    fun platformFailuresAreContained() {
        enable(anr = true, oom = false)
        profiler.failure = IllegalStateException("profiling service unavailable")

        collector().start()

        assertThat(submitted).isEmpty()
    }

    @Test
    fun manualModePreparesSourceFactsWithoutClaimingHostTriggersOrRegisteringAListener() {
        enable(anr = true, oom = true)
        profiler.triggerKinds += setOf(ANR, OOM)
        val collector = collector(configuration = manualConfiguration())

        collector.start()
        profiler.deliver(ANR, "/host")

        assertThat(profiler.events).containsExactly("summary", "unregister")
        assertThat(profiler.triggerKinds).containsExactlyInAnyOrder(ANR, OOM)
        assertThat(CrashProfileSource.oomSource).isNotNull()
        assertThat(submitted).isEmpty()
    }

    @Test
    fun switchingToManualRemovesOnlyPreviouslyClaimedTriggers() {
        enable(anr = true, oom = false)
        collector().start()
        profiler.triggerKinds += OOM
        profiler.events.clear()

        collector(configuration = manualConfiguration()).start()

        assertThat(profiler.events).containsExactly("summary", "remove=[anr]", "unregister")
        assertThat(profiler.triggerKinds).containsExactly(OOM)
    }

    @Test
    fun borrowedImportOnlySubmitsThePrivateCopyAndAcknowledgesStorageWithoutManagingTriggers() {
        enable(anr = true, oom = false)
        var result: CaptureResult<Unit>? = null
        val source = File("/caller-owned/anr")

        collector(configuration = manualConfiguration()).importProfile(source, CrashProfileKind.ANR) { result = it }

        verify(store).importProfile(source, CrashProfileKind.ANR)
        verify(store, never()).adopt(any(), any())
        assertThat(profiler.events).isEmpty()
        assertThat(submitted).containsExactly(Triple("/sdk-private/imported.anr", ANR, true))
        assertThat(result).isEqualTo(CaptureResult.Success(Unit))
    }

    @Test
    fun disabledKindRejectsImportWithoutAccessingTheSource() {
        enable(anr = true, oom = false)
        var result: CaptureResult<Unit>? = null

        collector(configuration = manualConfiguration()).importProfile(File("/caller-owned/oom"), CrashProfileKind.OOM) { result = it }

        verify(store, never()).importProfile(any(), any())
        assertThat(result).isInstanceOf(CaptureResult.Failure::class.java)
        assertThat(submitted).isEmpty()
    }

    @Test
    fun disabledModeRejectsImportWithoutAccessingTheSource() {
        enable(anr = true, oom = true)
        var result: CaptureResult<Unit>? = null

        collector(configuration = Configuration()).importProfile(File("/caller-owned/anr"), CrashProfileKind.ANR) { result = it }

        verify(store, never()).validateSource(any())
        verify(store, never()).importProfile(any(), any())
        assertThat(result).isInstanceOf(CaptureResult.Failure::class.java)
        assertThat(encoded).isEmpty()
        assertThat(submitted).isEmpty()
    }

    @Test
    fun importStorageFailureIsReportedWithoutSubmittingTheOriginal() {
        enable(anr = true, oom = false)
        whenever(store.importProfile(any(), any())).thenAnswer { throw IOException("disk full") }
        var result: CaptureResult<Unit>? = null

        collector(configuration = manualConfiguration()).importProfile(File("/caller-owned/anr"), CrashProfileKind.ANR) { result = it }

        assertThat((result as CaptureResult.Failure).error.message).isEqualTo("disk full")
        assertThat(submitted).isEmpty()
    }

    @Test
    fun realPublicationFailureReportsFailureAndKeepsTheBorrowedOriginal() {
        enable(anr = true, oom = false)
        val directory = File(temporary.root, "profiles")
        val source = temporary.newFile("borrowed-failure").apply { writeText("profile") }
        val operations =
            object : CrashProfileFileOperations() {
                override fun move(
                    source: File,
                    target: File,
                ): Unit = throw IOException("publication failed")
            }
        val executor = QueuedExecutor()
        var result: CaptureResult<Unit>? = null
        val collector = collector(executor = executor, profileStore = CrashProfileStore(directory, runtime, operations))

        collector.importProfile(source, CrashProfileKind.ANR) { result = it }
        assertThat(result).isNull()
        executor.drain()

        assertThat((result as CaptureResult.Failure).error.message).isEqualTo("publication failed")
        assertThat(source.readText()).isEqualTo("profile")
        assertThat(directory.listFiles()).isEmpty()
        assertThat(submitted).isEmpty()
    }

    @Test
    fun unavailableExecutorReportsFailureExactlyOnce() {
        var callbacks = 0
        val collector = collector(executor = Executor { throw RejectedExecutionException("stopped") })

        collector.importProfile(File("/caller-owned/anr"), CrashProfileKind.ANR) {
            assertThat(it).isInstanceOf(CaptureResult.Failure::class.java)
            callbacks++
        }

        assertThat(callbacks).isEqualTo(1)
        verify(store, never()).importProfile(any(), any())
    }

    @Test
    fun newlyEnabledKindsAlsoReplayTheirPendingProfiles() {
        enable(anr = true, oom = false)
        pending += listOf(ANR to "/previous-anr", OOM to "/previous-oom")
        val executor = QueuedExecutor()
        val listener = SafeEventListenerLogger(collector(executor = executor))
        listener.start()
        executor.drain()

        enable(anr = true, oom = true)
        listener.start()
        executor.drain()
        assertThat(submitted).containsExactly(Triple("/previous-anr", ANR, false))

        listener.stop()
        listener.start()
        executor.drain()

        assertThat(submitted).containsExactly(Triple("/previous-anr", ANR, false), Triple("/previous-oom", OOM, false))
    }

    @Test
    fun cachedOomFactsFollowListenerStartsNotLiveFlagChanges() {
        enable(anr = false, oom = true)
        val executor = QueuedExecutor()
        val listener = SafeEventListenerLogger(collector(executor = executor))
        listener.start()
        executor.drain()
        val source = CrashProfileSource.oomSource
        assertThat(source).isNotNull()

        enable(anr = false, oom = false)
        listener.start()
        executor.drain()
        assertThat(CrashProfileSource.oomSource).isSameAs(source)
        assertThat(profiler.triggerKinds).containsExactly(OOM)

        listener.stop()
        executor.drain()
        assertThat(CrashProfileSource.oomSource).isNull()
        // Persistent OS triggers are reconciled on the next start, not stop.
        assertThat(profiler.triggerKinds).containsExactly(OOM)
        listener.start()
        executor.drain()
        assertThat(profiler.triggerKinds).isEmpty()
    }

    @Test
    fun queuedPlatformCallbackCannotAdoptAResultAfterStopOrAfterRestart() {
        enable(anr = true, oom = false)
        val executor = QueuedExecutor()
        val directory = File(temporary.root, "profiles")
        val source = temporary.newFile("late").apply { writeText("profile") }
        val listener = SafeEventListenerLogger(collector(executor = executor, profileStore = CrashProfileStore(directory, runtime)))
        listener.start()
        executor.drain()
        val stale = profiler.captureResult(ANR, source.path)
        executor.execute(stale)

        listener.stop()
        executor.drain()
        assertThat(source.readText()).isEqualTo("profile")
        assertThat(submitted).isEmpty()

        listener.start()
        executor.drain()
        executor.execute(stale)
        executor.drain()
        assertThat(source.readText()).isEqualTo("profile")
        assertThat(submitted).isEmpty()

        executor.execute(profiler.captureResult(ANR, source.path))
        executor.drain()
        assertThat(source).doesNotExist()
        assertThat(submitted).hasSize(1)
        assertThat(File(submitted.single().first).readText()).isEqualTo("profile")
    }

    @Test
    fun queuedImportIsRejectedOnceAfterStopAndCannotRunAgainstReinitializedCollector() {
        enable(anr = true, oom = true)
        val executor = QueuedExecutor()
        val directory = File(temporary.root, "profiles")
        val source = temporary.newFile("borrowed-queued").apply { writeText("profile") }
        val first = collector(executor = executor, profileStore = CrashProfileStore(directory, runtime))
        val listener = SafeEventListenerLogger(first)
        listener.start()
        executor.drain()
        val results = mutableListOf<CaptureResult<Unit>>()
        first.importProfile(source, CrashProfileKind.ANR, results::add)
        listener.stop()
        val replacement = SafeEventListenerLogger(collector(executor = executor, profileStore = CrashProfileStore(directory, runtime)))
        replacement.start()
        executor.drain()

        assertThat(results).hasSize(1).allSatisfy { assertThat(it).isInstanceOf(CaptureResult.Failure::class.java) }
        assertThat(source.readText()).isEqualTo("profile")
        assertThat(submitted).isEmpty()
        assertThat(directory.listFiles().orEmpty()).isEmpty()
        assertThat(CrashProfileSource.current(context, ::encode)).isNotNull()
        assertThat(CrashProfileSource.oomSource).isNotNull()
    }

    @Test
    fun stopBeforeQueuedStartDoesNotRegisterOrPrepareSourceFacts() {
        enable(anr = true, oom = true)
        val executor = QueuedExecutor()
        val listener = SafeEventListenerLogger(collector(executor = executor))

        listener.start()
        listener.start()
        listener.stop()
        executor.drain()

        assertThat(encoded).isEmpty()
        assertThat(profiler.events).containsExactly("unregister")
        assertThat(profiler.triggerKinds).isEmpty()
        assertThat(CrashProfileSource.oomSource).isNull()
    }

    @Test
    fun stopDuringImportKeepsThePersistedCopyForRestartInsteadOfSubmittingToAnotherLogger() {
        enable(anr = true, oom = false)
        val executor = QueuedExecutor()
        val directory = File(temporary.root, "profiles")
        val source = temporary.newFile("borrowed-in-flight").apply { writeText("profile") }
        lateinit var listener: SafeEventListenerLogger
        val operations =
            object : CrashProfileFileOperations() {
                override fun syncFile(file: File) {
                    super.syncFile(file)
                    listener.stop()
                }
            }
        val first = collector(executor = executor, profileStore = CrashProfileStore(directory, runtime, operations))
        listener = SafeEventListenerLogger(first)
        listener.start()
        executor.drain()
        var result: CaptureResult<Unit>? = null

        first.importProfile(source, CrashProfileKind.ANR) { result = it }
        executor.drain()

        assertThat(result).isEqualTo(CaptureResult.Success(Unit))
        assertThat(source.readText()).isEqualTo("profile")
        val imported = directory.listFiles()!!.single()
        assertThat(imported.readText()).isEqualTo("profile")
        assertThat(submitted).isEmpty()

        SafeEventListenerLogger(collector(executor = executor, profileStore = CrashProfileStore(directory, runtime))).start()
        executor.drain()
        assertThat(submitted).containsExactly(Triple(imported.path, ANR, false))
    }

    @Test
    fun shutdownRejectsNewImportWithoutLosingAnAlreadyQueuedCompletion() {
        enable(anr = true, oom = false)
        val executor = QueuedExecutor()
        val collector = collector(executor = executor)
        val listener = SafeEventListenerLogger(collector)
        listener.start()
        executor.drain()
        var completions = 0
        val complete: (CaptureResult<Unit>) -> Unit = {
            assertThat(it).isInstanceOf(CaptureResult.Failure::class.java)
            completions++
        }
        collector.importProfile(File("/caller-owned/queued"), CrashProfileKind.ANR, complete)
        listener.stop()
        executor.shutdown = true
        collector.importProfile(File("/caller-owned/rejected"), CrashProfileKind.ANR, complete)
        executor.drain()

        assertThat(completions).isEqualTo(2)
        verify(store, never()).importProfile(any(), any())
        assertThat(submitted).isEmpty()
    }

    @Test
    fun importQueuedBeforeFirstListenerStartIsNotMistakenForStoppedWork() {
        enable(anr = true, oom = false)
        val executor = QueuedExecutor()
        val collector = collector(configuration = manualConfiguration(), executor = executor)
        var result: CaptureResult<Unit>? = null

        collector.importProfile(File("/caller-owned/anr"), CrashProfileKind.ANR) { result = it }
        SafeEventListenerLogger(collector).start()
        executor.drain()

        assertThat(result).isEqualTo(CaptureResult.Success(Unit))
        assertThat(submitted).containsExactly(Triple("/sdk-private/imported.anr", ANR, true))
    }

    @Test
    fun persistedImportSurvivesNativeSubmissionFailureAndOriginalDeletionUntilNextStartup() {
        enable(anr = true, oom = false)
        val directory = File(temporary.root, "profiles")
        val profileStore = CrashProfileStore(directory, runtime)
        val source = temporary.newFile("borrowed").apply { writeText("profile") }
        var result: CaptureResult<Unit>? = null
        val first =
            collector(
                configuration = manualConfiguration(),
                profileStore = profileStore,
                submit = { _, _, _ -> throw IllegalStateException("native attribution unavailable") },
            )

        first.importProfile(source, CrashProfileKind.ANR) { result = it }

        assertThat(result).isEqualTo(CaptureResult.Success(Unit))
        assertThat(source.readText()).isEqualTo("profile")
        assertThat(source.delete()).isTrue()
        val imported = directory.listFiles()!!.single()
        assertThat(imported.readText()).isEqualTo("profile")

        collector(configuration = manualConfiguration(), profileStore = CrashProfileStore(directory, runtime)).start()

        assertThat(submitted).containsExactly(Triple(imported.path, ANR, false))
    }

    @Test
    fun automaticCollectionAdoptsIntoPrivateStorageButLeavesOtherResultsAlone() {
        enable(anr = true, oom = false)
        val directory = File(temporary.root, "profiles")
        val source = temporary.newFile("owned").apply { writeText("profile") }
        val unrelated = temporary.newFile("host").apply { writeText("host") }
        collector(profileStore = CrashProfileStore(directory, runtime)).start()

        profiler.deliver(ANR, source.path)
        profiler.deliver(null, unrelated.path)

        val imported = directory.listFiles()!!.single()
        assertThat(source).doesNotExist()
        assertThat(imported.readText()).isEqualTo("profile")
        assertThat(submitted).containsExactly(Triple(imported.path, ANR, true))
        assertThat(unrelated.readText()).isEqualTo("host")
    }

    @Test
    fun automaticCollectionCleansUpItsOriginalEvenIfStorageRejectsIt() {
        enable(anr = true, oom = false)
        whenever(store.adopt(any(), any())).thenAnswer { throw IOException("disk full") }
        val source = temporary.newFile("owned").apply { writeText("profile") }
        collector().start()

        profiler.deliver(ANR, source.path)

        assertThat(source).doesNotExist()
        assertThat(submitted).isEmpty()
    }

    @Test
    fun privatePendingFilesAreExpiredEvenWhileProfilingIsDisabled() {
        val directory = File(temporary.root, "profiles").apply { mkdirs() }
        val expired =
            File(directory, "old.anr").apply {
                writeText("old")
                setLastModified(System.currentTimeMillis() - CrashProfileStore.MAX_AGE_MS - 1000)
            }

        collector(profileStore = CrashProfileStore(directory, runtime)).start()

        assertThat(expired).doesNotExist()
        assertThat(submitted).isEmpty()
        assertThat(profiler.pendingReads).isZero()
    }

    @Test
    fun rejectedPrivateSourceIsNotTouchedByPendingReplayOrCleanup() {
        enable(anr = true, oom = false)
        val directory = File(temporary.root, "profiles").apply { mkdirs() }
        val source =
            File(directory, "old.anr").apply {
                writeText("original")
                setLastModified(System.currentTimeMillis() - CrashProfileStore.MAX_AGE_MS - 1000)
            }
        var result: CaptureResult<Unit>? = null

        collector(profileStore = CrashProfileStore(directory, runtime)).importProfile(source, CrashProfileKind.ANR) { result = it }

        assertThat(result).isInstanceOf(CaptureResult.Failure::class.java)
        assertThat(source.readText()).isEqualTo("original")
        assertThat(submitted).isEmpty()
    }

    private fun manualConfiguration() = Configuration(crashProfilingMode = CrashProfilingMode.MANUAL)

    private fun collector(
        supportsOom: Boolean = true,
        configuration: Configuration = Configuration(crashProfilingMode = CrashProfilingMode.AUTOMATIC),
        executor: Executor = Executor(Runnable::run),
        profileStore: CrashProfileStore = store,
        profilePreferences: IPreferences = preferences,
        submit: (String, String, Boolean) -> Unit = { path, kind, retain -> submitted += Triple(path, kind, retain) },
    ) = CrashProfileCollector(
        context,
        profiler,
        runtime,
        { facts ->
            encoded += facts
            encode(facts)
        },
        submit,
        executor,
        supportsOom,
        configuration,
        profilePreferences,
        profileStore,
    )

    private fun encode(facts: CrashProfileFacts): ByteArray = facts.toString().toByteArray()

    private fun enable(
        anr: Boolean,
        oom: Boolean,
    ) {
        whenever(runtime.isEnabled(RuntimeFeature.CRASH_PROFILES_ANR)).thenReturn(anr)
        whenever(runtime.isEnabled(RuntimeFeature.CRASH_PROFILES_OOM)).thenReturn(oom)
    }

    private class FakeProfiler : ISystemTriggerProfiler {
        val events = mutableListOf<String>()
        val summaries = mutableListOf<ByteArray>()
        var failure: RuntimeException? = null
        val pending = mutableListOf<Pair<String, String>>()
        val triggerKinds = mutableSetOf<String>()
        var pendingReads = 0
        var pendingFailure: RuntimeException? = null
        private var onResult: ((String?, String?) -> Unit)? = null

        override fun addTriggers(kinds: Set<String>) {
            failure?.let { throw it }
            events += "add=${kinds.sorted()}"
            triggerKinds += kinds
        }

        override fun removeTriggers(kinds: Set<String>) {
            failure?.let { throw it }
            events += "remove=${kinds.sorted()}"
            triggerKinds -= kinds
        }

        override fun setProcessStateSummary(summary: ByteArray) {
            failure?.let { throw it }
            events += "summary"
            summaries += summary
        }

        override fun register(onResult: (kind: String?, path: String?) -> Unit) {
            if (this.onResult == null) {
                events += "register"
                this.onResult = onResult
            }
        }

        override fun unregister() {
            events += "unregister"
            onResult = null
        }

        override fun pendingResults(): List<Pair<String, String>> {
            pendingReads++
            pendingFailure?.let { throw it }
            return pending
        }

        fun deliver(
            kind: String?,
            path: String?,
        ) {
            onResult?.invoke(kind, path)
        }

        fun captureResult(
            kind: String?,
            path: String?,
        ): Runnable {
            val callback = checkNotNull(onResult)
            return Runnable { callback(kind, path) }
        }
    }

    private class QueuedExecutor : Executor {
        private val queued = ArrayDeque<Runnable>()
        var shutdown = false

        override fun execute(command: Runnable) {
            if (shutdown) throw RejectedExecutionException("stopped")
            queued.addLast(command)
        }

        fun drain() {
            while (queued.isNotEmpty()) queued.removeFirst().run()
        }
    }
}
