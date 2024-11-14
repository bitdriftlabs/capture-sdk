package io.bitdrift.capture.replay

interface IInternalLogger {
    /**
     * Forwards a verbose message internally to the SDK
     */
    fun logVerboseInternal(message: String, fields: Map<String, String>? = null)

    /**
     * Forwards a debug message internally to the SDK
     */
    fun logDebugInternal(message: String, fields: Map<String, String>? = null)

    /**
     * Forwards an error message internally to the SDK
     */
    fun logErrorInternal(message: String, e: Throwable? = null, fields: Map<String, String>? = null)
}