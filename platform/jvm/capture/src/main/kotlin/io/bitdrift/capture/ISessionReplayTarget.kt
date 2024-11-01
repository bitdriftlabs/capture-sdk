package io.bitdrift.capture

/**
 * Responsible for emitting session replay screen and screenshot logs.
 */
interface ISessionReplayTarget {
    /**
     * Called to indicate that the target is supposed to prepare and emit a session replay screen log.
     */
    fun captureScreen()

    /**
     * Called to indicate that the target is supposed to prepare and emit a session replay screenshot log.
     */
    fun captureScreenshot()
}
