// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.error.IErrorReporter
import io.bitdrift.capture.network.ICaptureNetwork
import io.bitdrift.capture.providers.FieldValue
import io.bitdrift.capture.providers.session.SessionStrategyConfiguration

// We use our own type here instead of a builtin function to allow us to avoid proguard-rewriting this class.

/**
 * Handle to allow for lazy loading of an exception stacktrace.
 */
interface StackTraceProvider {
    /**
     * Invoked to produce the stack trace for the exception that is being reported.
     */
    fun invoke(): String?
}

@Suppress("UndocumentedPublicClass")
internal object CaptureJniLibrary : IBridge {
    /**
     * Loads the shared library. This is safe to call multiple times.
     */
    fun load() {
        // Just testing ci. Will remove
        System.loadLibrary("capture")
    }

    /**
     * Creates a new logger, returning a handle that can be used to interact with the logger.
     *
     * @param sdkDirectory the directory to use when persisting data and/or configuration
     * @param apiKey the key used to authenticate the application with Bitdrift services.
     * @param sessionStrategy the session strategy to use.
     * @param metadataProvider used to provide metadata for emitted logs.
     * @param resourceUtilizationTarget used to inform platform layer about a need to emit a resource log.
     * @param sessionReplayTarget used to inform platform layer about a need to emit session replay logs.
     * @param eventsListenerTarget responsible for listening to platform events and emitting logs in response to them.
     * @param applicationId the application ID of the current app, used to identify with the backend
     * @param applicationVersion the version of the current app, used to identify with the backend
     * @param model the host device model, used to identify with the backend
     * @param network the network implementation to use to communicate with the backend
     * @param preferences the preferences storage to use for persistent storage of simple settings and configuration.
     * @param errorReporter the error reporter to use for reporting error to bitdrift services.
     * @param startInSleepMode true to initialize in sleep mode
     */
    external override fun createLogger(
        sdkDirectory: String,
        apiKey: String,
        sessionStrategy: SessionStrategyConfiguration,
        metadataProvider: IMetadataProvider,
        resourceUtilizationTarget: IResourceUtilizationTarget,
        sessionReplayTarget: ISessionReplayTarget,
        eventsListenerTarget: IEventsListenerTarget,
        applicationId: String,
        applicationVersion: String,
        model: String,
        network: ICaptureNetwork,
        preferences: IPreferences,
        errorReporter: IErrorReporter,
        startInSleepMode: Boolean,
    ): Long

    /**
     * Starts the logger. You must call this function exactly once before you start using created
     * logger to emit logs.
     *
     * @param loggerId the ID of the logger to start.
     */
    external fun startLogger(loggerId: Long)

    /**
     * Destroys the logger associated with the provided logger id. If called more than once for a
     * given logger, subsequent calls will be no-ops.
     *
     * @param loggerId the ID of the logger to destroy
     */
    external fun destroyLogger(loggerId: Long)

    /**
     * Starts a new session using configured session strategy.
     *
     * @param loggerId the logger to start a new session for.
     */
    external fun startNewSession(loggerId: Long)

    /**
     * Returns currently active session Id.
     *
     * @param loggerId the logger to get the active session Id from.
     */
    external fun getSessionId(loggerId: Long): String?

    /**
     * Returns the device ID. The ID is generated the first time it is accessed.
     * Consecutive calls to this method return the same value.
     */
    external fun getDeviceId(loggerId: Long): String?

    /**
     * Adds a field that should be attached to all logs emitted by the logger going forward.
     * If a field with a given key has already been registered with the logger, its value is
     * overridden with the new value.
     *
     * Fields added with this method take precedence over fields returned by registered `FieldProvider`s
     * and are overwritten by custom logs emitted.
     *
     * @param loggerId the logger to add the field to.
     * @param key the name of the field to add.
     * @param value the value of the field to add.
     */
    external fun addLogField(
        loggerId: Long,
        key: String,
        value: String,
    )

    /**
     * Removes a field with a given key. This operation does nothing if the field
     * with the given key is not registered with the logger.
     *
     * @param loggerId the logger to remove the field from.
     * @param key the name of the field to remove.
     */
    external fun removeLogField(
        loggerId: Long,
        key: String,
    )

    /**
     * Writes a log to the Capture logger.
     *
     * @param loggerId the ID of the logger to write to.
     * @param logType the type of the log to be logged.
     * @param logLevel the log level of the log.
     * @param log the log message of the log.
     * @param fields fields map of arbitrary key-value fields to include with the log.
     * @param matchingFields fields map of arbitrary key-value fields that can be read when processing
     *        a given log but are not a part of the log itself.
     * @param overrideExpectedPreviousProcessSessionId if provided, it will be assigned as a session ID
     *        of the log. The passed value has to match the last session ID active during the previous run of
     *        the SDK.
     * @param overrideOccurredAtUnixMilliseconds used to override the timestamp of the log. Used only if
     *        valid `override_expected_previous_process_session_id` is provided, ignored otherwise.
     * @param blocking if true, the call blocks until the log has been processed.
     */
    external fun writeLog(
        loggerId: Long,
        logType: Int,
        logLevel: Int,
        log: String,
        fields: Map<String, FieldValue>,
        matchingFields: Map<String, FieldValue>,
        overrideExpectedPreviousProcessSessionId: String?,
        overrideOccurredAtUnixMilliseconds: Long,
        blocking: Boolean,
    )

    /**
     * Writes a session replay log.
     *
     * @param loggerId the ID of the logger to write to.
     * @param fields the fields to include with the log.
     * @param duration the duration of time the preparation of the session replay log took, in seconds.
     */
    external fun writeSessionReplayScreenLog(
        loggerId: Long,
        fields: Map<String, FieldValue>,
        duration: Double,
    )

    /**
     * Writes a session replay screenshot log.
     *
     * @param loggerId the ID of the logger to write to.
     * @param fields the fields to include with the log.
     * @param duration the duration of time the preparation of the session replay log took, in seconds.
     */
    external fun writeSessionReplayScreenshotLog(
        loggerId: Long,
        fields: Map<String, FieldValue>,
        duration: Double,
    )

    /**
     * Writes a resource utilization log.
     *
     * @param loggerId the ID of the logger to write to.
     * @param fields the fields to include with the log.
     * @param duration the duration of time the preparation of the resource log took, in seconds.
     */
    external fun writeResourceUtilizationLog(
        loggerId: Long,
        fields: Map<String, FieldValue>,
        duration: Double,
    )

    /**
     * Writes an SDK started log.
     *
     * @param loggerId the ID of the logger to write to.
     * @param fields the fields to include with the log.
     * @param durationMs the duration of time the SDK configuration took.
     */
    external fun writeSDKStartLog(
        loggerId: Long,
        fields: Map<String, FieldValue>,
        duration: Double,
    )

    /**
     * Checks whether the app update log should be written.
     *
     * @param loggerId the ID of the logger to write to use.
     * @param appVersion the version of the app.
     * @param appVersionCode the app build number.
     */
    external fun shouldWriteAppUpdateLog(
        loggerId: Long,
        appVersion: String,
        appVersionCode: Long,
    ): Boolean

    /**
     * Writes an app update log.
     *
     * @param loggerId the ID of the logger to write to.
     * @param appVersion
     * @param appVersionCode
     * @param appInstallSizeBytes the size of the app installation. Expressed in bytes.
     * @param durationS the duration of time the preparation of the log took. Expressed in seconds.
     */
    external fun writeAppUpdateLog(
        loggerId: Long,
        appVersion: String,
        appVersionCode: Long,
        appInstallSizeBytes: Long,
        durationS: Double,
    )

    /**
     * Writes an app launch TTI log. The method should be called only once per logger Id. Consecutive calls
     * have no effect.
     *
     * @param loggerId the ID of the logger to write to.
     * @param durationS the time between a user's intent to launch the app and when the app becomes
     *                  interactive. Expressed in seconds. Calls with a negative duration are ignored.
     */
    external fun writeAppLaunchTTILog(
        loggerId: Long,
        durationS: Double,
    )

    /**
     * Writes a screen view log.
     *
     * @param loggerId the ID of the logger to write to.
     * @param screenName the name of the screen.
     */
    external fun writeScreenViewLog(
        loggerId: Long,
        screenName: String,
    )

    /**
     * Flushes logger's state to disk.
     *
     * @param blocking Whether the method should return only after the flushing completes.
     */
    external fun flush(
        loggerId: Long,
        blocking: Boolean,
    )

    /**
     * Writes an error message to the internal debugger. Using this over other native logging
     * mechanisms has the benefit of being correctly sequenced in with other logging calls with no
     * chance of races.
     */
    external fun debugError(message: String)

    /**
     * Writes an debug message to the internal debugger. Using this over other native logging
     * mechanisms has the benefit of being correctly sequenced in with other logging calls with no
     * chance of races.
     */
    external fun debugDebug(message: String)

    /**
     * Sets the log level of the internal bitdrift debugger to DEBUG.
     */
    external fun enableDebugLogging()

    /**
     * Called to report an error via the ErrorReporter. This is preferred over calling the ErrorReporter
     * directly as it allows for centralized control over error flood controls.
     */
    external fun reportError(
        message: String,
        stackTraceProvider: StackTraceProvider,
    )

    /**
     * Sets sleep mode on or off
     *
     * @param loggerId the ID of the logger to update
     * @param enabled true if sleep mode should be activated
     */
    external fun setSleepModeEnabled(
        loggerId: Long,
        enabled: Boolean,
    )
}
