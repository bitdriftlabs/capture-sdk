// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.profiling

/**
 * Selects who manages Android's profiling triggers, listeners, and original result files.
 * Set [io.bitdrift.capture.Configuration.crashProfilingMode] to opt in; `null` disables profiling.
 * Both modes require fatal issue reporting and the corresponding runtime flags to be enabled.
 */
enum class CrashProfilingMode {
    /** Capture registers triggers and a listener, and consumes the result files it owns. */
    AUTOMATIC,

    /**
     * The app manages triggers, listeners, and original files, and calls
     * [io.bitdrift.capture.Capture.Logger.importCrashProfile] to import a private copy.
     * Capture still prepares source facts for attribution, including the ANR process-state summary.
     */
    MANUAL,
}

/** The system-triggered crash profile supplied to Capture. */
enum class CrashProfileKind(
    internal val value: String,
) {
    /** An Android 16+ system-triggered ANR trace. */
    ANR("anr"),

    /** An Android 17+ system-triggered Java OutOfMemoryError heap dump. */
    OOM("oom"),
}
