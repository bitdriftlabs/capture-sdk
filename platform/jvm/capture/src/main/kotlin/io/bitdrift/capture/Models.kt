// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture

/**
 * A monad for modeling success or failure operations in the Capture SDK.
 */
sealed class CaptureResult<out V> {
    /**
     * Represents a successful operation result within the Capture SDK.
     *
     * @param V the type of value returned upon success.
     * @property value the result value produced by the operation.
     */
    data class Success<V>(
        val value: V,
    ) : CaptureResult<V>()

    /**
     * Represents a failed operation result within the Capture SDK.
     *
     * @property error the [Error] describing the reason for the failure.
     */
    data class Failure(
        val error: Error,
    ) : CaptureResult<Nothing>()
}

/**
 * Represents a failed operation in the Capture SDK.
 * @param message A message describing the error.
 */
sealed class Error(
    open val message: String,
)

/**
 * Represents a failed operation due to an API error.
 * @param message A message describing the error.
 */
sealed class ApiError(
    override val message: String,
) : Error(message) {
    /**
     * Represents a failed API operation due to a network I/O error.
     * @param message A message describing the error.
     */
    data class NetworkError(
        override val message: String,
    ) : ApiError(message)

    /**
     * Represents a failed API operation due to a server error.
     * @param statusCode The HTTP status code of the server error.
     * @param body The body of the server error response if any.
     */
    data class ServerError(
        val statusCode: Int,
        val body: String?,
    ) : ApiError("${body ?: "Server Error"}. statusCode=$statusCode")

    /**
     * Represents a failed API operation due to a serialization error.
     * @param message A message describing the error.
     */
    data class SerializationError(
        override val message: String,
    ) : ApiError(message)
}

/**
 * Represents a failed operation due to the SDK not being started
 */
data object SdkNotStartedError : Error("SDK not started")
