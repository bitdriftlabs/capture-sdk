package io.bitdrift.capture.reports.jvmcrash

/**
 * [java.lang.Thread.UncaughtExceptionHandler] that will notify crashes into the added [io.bitdrift.capture.reports.jvmcrash.JvmCrashListener]
 */
interface ICaptureUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
    /**
     * Installs the [java.lang.Thread.UncaughtExceptionHandler] and notifies [io.bitdrift.capture.reports.jvmcrash.JvmCrashListener] when
     * a JVM crash occurs
     */
    fun install(jvmCrashListener: JvmCrashListener)

    /**
     * Uninstalls [java.lang.Thread.UncaughtExceptionHandler]
     */
    fun uninstall()
}
