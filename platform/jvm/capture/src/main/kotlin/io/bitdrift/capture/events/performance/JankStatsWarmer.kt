// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.events.performance

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.WorkerThread
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.FrameDataApi24
import androidx.metrics.performance.FrameDataApi31
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import androidx.metrics.performance.StateInfo
import io.bitdrift.capture.IInternalLogger
import io.bitdrift.capture.LogLevel
import io.bitdrift.capture.LogType
import io.bitdrift.capture.common.IBackgroundThreadHandler
import io.bitdrift.capture.providers.ArrayFields
import io.bitdrift.capture.threading.CaptureDispatchers
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Does the one-time work that [JankStats] would otherwise do on the main thread the first time it
 * tracks a window.
 */
internal fun interface IJankStatsWarmer {
    /**
     * Starts the warm-up without blocking the calling thread. [onComplete] is invoked once, on a
     * background thread, when JankStats can be used without that one-time work (or when warming up
     * wasn't possible and JankStats should be used as is).
     */
    fun warmUp(onComplete: () -> Unit)
}

/**
 * On API 24+ JankStats creates its "FrameMetricsAggregator" [HandlerThread] lazily, on the main
 * thread, and blocks in [HandlerThread.getLooper] until that thread has started. On a loaded or
 * thermally throttled device that wait can last long enough to ANR. The handler is kept in a static
 * field, so creating it here means JankStats finds it already set and never waits. Nothing waits
 * for the thread to start either: the handler is stored from the thread's own
 * [HandlerThread.onLooperPrepared], so the shared background executor is never blocked.
 *
 * It also loads and initializes the JankStats classes so that the first
 * [JankStats.createAndTrack] call on the main thread does not pay for class loading and
 * verification.
 *
 * Every step is best effort: if the library internals are not where we expect them, JankStats
 * falls back to its own behavior.
 */
internal class JankStatsWarmer(
    private val logger: IInternalLogger,
    private val backgroundThreadHandler: IBackgroundThreadHandler = CaptureDispatchers.CommonBackground,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : IJankStatsWarmer {
    override fun warmUp(onComplete: () -> Unit) {
        backgroundThreadHandler.runAsync {
            val field = if (sdkInt >= Build.VERSION_CODES.N) findUnsetFrameMetricsHandlerField() else null
            if (field == null) {
                preloadClasses()
                onComplete()
                return@runAsync
            }
            FrameMetricsHandlerThread { looper ->
                preloadClasses()
                seedFrameMetricsHandler(field, Handler(looper))
                onComplete()
            }.start()
        }
    }

    private fun preloadClasses() {
        val publicClassNames =
            listOf(
                JankStats::class.java,
                JankStats.OnFrameListener::class.java,
                PerformanceMetricsState::class.java,
                PerformanceMetricsState.Holder::class.java,
                FrameData::class.java,
                FrameDataApi24::class.java,
                FrameDataApi31::class.java,
                StateInfo::class.java,
            ).map { it.name }
        (publicClassNames + internalClassNamesFor(sdkInt)).forEach(::loadAndInitialize)
    }

    private fun loadAndInitialize(className: String) {
        try {
            Class.forName(className, true, JankStats::class.java.classLoader)
        } catch (_: ClassNotFoundException) {
            // Not present in this version of the library
        } catch (_: LinkageError) {
            // Leave it for JankStats to load (and report) on first use
        }
    }

    /**
     * Returns the handler field only when JankStats still has to create its handler. Logs and returns
     * null when the field can't be found, in which case JankStats creates the handler itself.
     */
    private fun findUnsetFrameMetricsHandlerField(): Field? {
        val field = findFrameMetricsHandlerField()
        if (field == null) {
            logger.logInternal(LogType.INTERNALSDK, LogLevel.WARNING, ArrayFields.EMPTY) {
                "Couldn't find JankStats frame metrics handler, it will be created on the main thread"
            }
            return null
        }
        return try {
            field.takeIf { it.get(null) == null }
        } catch (e: ReflectiveOperationException) {
            logSeedFailure(e)
            null
        } catch (e: IllegalArgumentException) {
            logSeedFailure(e)
            null
        }
    }

    @WorkerThread
    private fun seedFrameMetricsHandler(
        field: Field,
        handler: Handler,
    ) {
        try {
            synchronized(lock) {
                if (field.get(null) == null) {
                    field.set(null, handler)
                    return
                }
            }
        } catch (e: ReflectiveOperationException) {
            logSeedFailure(e)
        } catch (e: IllegalArgumentException) {
            logSeedFailure(e)
        }
        // Another warm-up (or JankStats itself) already set a handler, so this thread isn't needed
        handler.looper.quit()
    }

    private fun logSeedFailure(throwable: Throwable) {
        logger.logInternal(LogType.INTERNALSDK, LogLevel.WARNING, ArrayFields.EMPTY, throwable) {
            "Couldn't set JankStats frame metrics handler, it will be created on the main thread"
        }
    }

    private fun findFrameMetricsHandlerField(): Field? =
        HANDLER_OWNER_CLASS_NAMES.firstNotNullOfOrNull { className ->
            try {
                Class
                    .forName(className)
                    .getDeclaredField(FRAME_METRICS_HANDLER_FIELD_NAME)
                    .takeIf { Modifier.isStatic(it.modifiers) && it.type == Handler::class.java }
                    ?.apply { isAccessible = true }
            } catch (_: ReflectiveOperationException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }

    /**
     * Invokes [onPrepared] on the new thread as soon as its looper exists, so nothing has to block
     * in [HandlerThread.getLooper] waiting for the thread to start.
     */
    private class FrameMetricsHandlerThread(
        private val onPrepared: (Looper) -> Unit,
    ) : HandlerThread(FRAME_METRICS_THREAD_NAME) {
        override fun onLooperPrepared() {
            onPrepared(checkNotNull(Looper.myLooper()))
        }
    }

    private companion object {
        private val lock = Any()

        private const val PACKAGE = "androidx.metrics.performance"

        // Matches the name JankStats gives the thread when it creates it itself
        private const val FRAME_METRICS_THREAD_NAME = "FrameMetricsAggregator"
        private const val FRAME_METRICS_HANDLER_FIELD_NAME = "frameMetricsHandler"

        // 1.0.0-beta02 and later keep the handler in DelegatingFrameMetricsListener, 1.0.0-beta01
        // kept it in JankStatsApi24Impl. Both names are kept by consumer-rules.pro.
        private val HANDLER_OWNER_CLASS_NAMES =
            listOf(
                "$PACKAGE.DelegatingFrameMetricsListener",
                "$PACKAGE.JankStatsApi24Impl",
            )

        // The internal classes (including nested ones) JankStats loads for this API level, across
        // 1.0.0-beta01 and later; names missing from the resolved version are skipped. Classes
        // built for newer API levels are skipped, since loading them on older devices only costs
        // verification time.
        private fun internalClassNamesFor(sdkInt: Int): List<String> =
            buildList {
                add("$PACKAGE.JankStatsBaseImpl")
                add("$PACKAGE.JankStatsBaseImpl\$Companion")
                add("$PACKAGE.JankStatsApi16Impl")
                add("$PACKAGE.JankStatsApi16Impl\$onFrameListenerDelegate\$1")
                add("$PACKAGE.OnFrameListenerDelegate")
                add("$PACKAGE.DelegatingOnPreDrawListener")
                add("$PACKAGE.DelegatingOnPreDrawListener\$Companion")
                if (sdkInt >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    add("$PACKAGE.JankStatsApi22Impl")
                    add("$PACKAGE.DelegatingOnPreDrawListener22")
                }
                if (sdkInt >= Build.VERSION_CODES.N) {
                    add("$PACKAGE.JankStatsApi24Impl")
                    add("$PACKAGE.JankStatsApi24Impl\$Companion")
                    add("$PACKAGE.DelegatingFrameMetricsListener")
                    add("$PACKAGE.DelegatingFrameMetricsListener\$Companion")
                }
                if (sdkInt >= Build.VERSION_CODES.O) add("$PACKAGE.JankStatsApi26Impl")
                if (sdkInt >= Build.VERSION_CODES.S) add("$PACKAGE.JankStatsApi31Impl")
            }
    }
}
