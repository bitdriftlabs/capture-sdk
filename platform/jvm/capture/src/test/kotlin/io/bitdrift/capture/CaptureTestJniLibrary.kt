// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

import io.bitdrift.capture.error.IErrorReporter
import io.bitdrift.capture.providers.FieldValue
import javax.annotation.CheckReturnValue

class UploadedLog(
    val level: Int,
    val message: String?,
    val fields: Map<String, FieldValue>,
    val sessionId: String,
    val rfc3339Timestamp: String,
)

object CaptureTestJniLibrary {
    // Starts the test API server which can be used to verify interactions between
    // the mux and a real gRPC server.
    external fun startTestApiServer(pingInterval: Int): Int

    // Stops the test API server.
    external fun stopTestApiServer()

    // Configures a test server with aggressive continuous uploads.
    external fun configureAggressiveContinuousUploads(streamId: Int)

    // Returns the next received log.
    external fun nextUploadedLog(): UploadedLog

    // Blocks until the next API stream has been established, returning the stream id.
    external fun awaitNextApiStream(): Int

    // Blocks until the currently running test api server receives a handshake response on the
    // specific stream. If a clientMetadata parameter is provided, the metadata provided by the
    // handshake is also compared with the provided map.
    @CheckReturnValue
    external fun awaitApiServerReceivedHandshake(
        streamId: Int,
        metadata: Map<String, String>? = null,
        metadataKeysToIgnore: List<String>? = null,
    ): Boolean

    // Blocks until the currently running test api server closes the specified stream,
    // for a total of waitTimeMs milliseconds. Returns true if the stream has already been
    // closed or is closed within the deadline.
    @CheckReturnValue
    external fun awaitApiServerStreamClosed(streamId: Int, waitTimeMs: Int): Boolean

    // Triggers a configuration push on the specified stream ID.
    external fun sendConfigurationUpdate(streamId: Int)

    // Blocks until the specified stream receives a configuration ack, for a total of
    // waitTimeMs milliseconds. Returns true if the stream has already been closed or is
    // closed within the deadline.
    external fun awaitConfigurationAck(streamId: Int, waitTimeMs: Int): Boolean

    // Exercises the error reporting logic against the provided reporter object by passing the provided String to it.
    external fun sendErrorMessage(message: String?, errorReporter: IErrorReporter)

    // Exercises a test which invokes a Java function via ObjectHandle, verifying that the current
    // exception is cleared and does not bubble up to the Java layer.
    external fun runExceptionHandlingTest()

    // Exercises a test which uploads a batch upload that exceeds the 1 MiB request buffer size.
    external fun runLargeUploadTest(logger: Long)

    // Runs key value storage tests.
    external fun runKeyValueStorageTest(preferences: Any)

    // Runs resource utilization target test.
    external fun runResourceUtilizationTargetTest(target: Any)

    // Runs session replay target test.
    external fun runSessionReplayTargetTest(target: Any)

    // Runs events listener target test.
    external fun runEventsListenerTargetTest(target: Any)

    // Issues a runtime update causing the specified feature to be marked as disabled.
    external fun disableRuntimeFeature(streamId: Int, feature: String)
}
