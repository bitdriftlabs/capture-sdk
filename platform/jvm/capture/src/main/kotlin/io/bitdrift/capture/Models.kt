package io.bitdrift.capture

/**
 * A type alias for modeling success or failure operations in the Capture SDK.
 */
typealias CaptureResult<V> = Result<V>

/**
 * Represents a failed operation in the Capture SDK.
 * @param message A message describing the error.
 */
sealed class Error(
    override val message: String,
) : Throwable()

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
 * Represents a failed operation due to the SDK not being started.
 */
data object SdkNotStartedError : Error("SDK not started")
