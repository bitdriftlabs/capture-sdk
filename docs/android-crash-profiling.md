# Android crash profiling

Capture attaches Android system-triggered ANR traces (Android 16+) and Java `OutOfMemoryError` heap
dumps (Android 17+) to matching fatal issue reports. Fatal issue reporting and the corresponding
`client_feature.android.crash_profiles.anr` or `client_feature.android.crash_profiles.oom` runtime
flag must be enabled.

Opting in below API 36 logs a warning and leaves crash profiling disabled. On API 36, the SDK
warns that only ANR profiles are supported; OOM profiles require API 37+. Neither warning prevents
SDK startup or changes ordinary fatal issue reporting.

For emulator and physical-device procedures, see the [Gradle test app manual-testing
guide](../platform/jvm/gradle-test-app/docs/crash-profiling.md).

## Runtime configuration

`Configuration.crashProfilingMode` is startup configuration: `null` disables profiling by default;
`AUTOMATIC` and `MANUAL` opt in to their respective collection modes. ANR/OOM runtime flags are
sampled when the platform-event listener starts, normally once per process after runtime
configuration loads. Changing only these flags does **not** immediately update Android trigger
registrations, the result listener, the process-state summary, or the cached OOM source. The next
process start (or a stop/start caused by the generic `platform_events.enabled` flag) reconciles
them. Repeated listener starts while already running are no-ops.

Native attribution independently checks the live per-kind flags before admitting profiles; unchanged
OS registrations do not imply continued upload admission after a flag is disabled. Already admitted
uploads follow the normal upload lifecycle. Manual imports also check the live flags and reject a
disabled kind before reading the caller's file. Previously claimed persistent triggers are removed
at the next listener start if the kind or local opt-in is disabled. Disabling generic platform
events unregisters the listener, but leaves persistent triggers for that later reconciliation. Stale
callbacks and queued imports cannot reactivate a stopped collector.

## Automatic collection

Opt in at SDK startup:

```kotlin
Configuration(crashProfilingMode = CrashProfilingMode.AUTOMATIC)
```

`CrashProfilingMode.AUTOMATIC` manages Android triggers and a result listener. Capture exclusively
owns the enabled trigger types and their original result files. Other consumers must not depend on
those originals remaining available. Unrelated trigger types and on-demand profiling results are
left alone.

Ownership markers are synchronously persisted before triggers are added. A failed write is logged
and stops reconciliation; the next listener start retries every desired marker, even if a failed
commit already updated the process's in-memory preferences. There is no background retry loop.
Failed ownership-marker clears are also retried at the next listener start, without repeating
successful platform removals in the same process. Reclaimed automatic types cancel their pending
clear. If the process dies before a clear reaches disk, its stale marker survives the restart; the
manual-mode handoff limitation below still applies.

Capture adopts completed results by renaming them into its private pending directory. Both
directories normally reside on the same filesystem, so this does not copy the payload. A
cross-filesystem source requires a copy followed by deletion. Once attributed, the existing uploader
compresses the profile into its upload store and consumes the pending original. No hard links are
used.

## Caller-managed collection

Use manual mode when the application coordinates Android profiling with other consumers:

```kotlin
Configuration(crashProfilingMode = CrashProfilingMode.MANUAL)
```

Capture does not add triggers or register a result listener in this mode. When transitioning from
automatic mode, it removes only trigger types previously recorded as SDK-owned.

Pass completed results to the experimental import API:

```kotlin
val kind = when (result.triggerType) {
    ProfilingTrigger.TRIGGER_TYPE_ANR -> CrashProfileKind.ANR
    ProfilingTrigger.TRIGGER_TYPE_OOM -> CrashProfileKind.OOM
    else -> null
}
if (result.errorCode == ProfilingResult.ERROR_NONE && kind != null) {
    result.resultFilePath?.let { path ->
        Capture.Logger.importCrashProfile(File(path), kind) { imported ->
            when (imported) {
                is CaptureResult.Success -> coordinator.captureFinishedReading(path)
                is CaptureResult.Failure -> coordinator.captureImportFailed(path, imported.error)
            }
        }
    }
}
```

The example assumes the appropriate Android API-level checks and an application-owned coordinator.
Opt into `ExperimentalBitdriftApi` at the call site.

The importer never modifies, renames, or deletes the caller's original, even on failure. Keep the
original readable and unchanged until completion, which is delivered on the main thread. There is no
cancellation handle. A successful result means Capture has persisted an independent raw copy; it
does not promise that the profile matched a report or was uploaded. Parsing, attribution,
compression, and upload occur afterward and can reject an unusable profile. An import rejected
because profiling is disabled does not read the source. Queued imports are rejected if their
collector stops before they begin. An import already copying may finish persisting after stop; its
private copy is recovered on the next SDK startup rather than being submitted through the stopped
collector.

The owner may release the original once all consumers finish reading, not merely when Capture
finishes. Capture's private snapshot needs one raw copy; automatic collection avoids that copy by
transferring ownership. This API takes uncompressed profiles, not previously compressed uploads.
Each successful invocation is a separate import; the application should avoid submitting the same
result repeatedly.

### Coordination still required

Android supports multiple result listeners, but only one configuration per trigger type.
Applications must coordinate trigger configuration, listener registration, and original-file
cleanup. Register listeners after Capture starts and obtain paths and trigger types from
`ProfilingResult`, not by parsing platform filenames.

TODO: Provide a durable ownership-handoff boundary when switching an installation from automatic to
manual collection. Trigger cleanup runs asynchronously after runtime configuration loads; Capture's
successful startup callback does not await it. Application triggers registered in that callback can
therefore be removed by later SDK cleanup. This transition is not currently race-free, and waiting
for ordinary SDK startup is not a trigger-handoff barrier.

Both modes prepare source facts required by Capture's attribution. In particular, Capture owns
`ActivityManager.setProcessStateSummary` while ANR profiling is enabled; another component must not
overwrite it. Java OOM reports also carry SDK source facts. Manual mode changes collection and file
ownership, not the attribution requirements.

### Known process-start attribution gap

TODO: Replace `Process.getStartElapsedRealtime()` with the kernel start time from `/proc/self/stat`,
converted with the device's `_SC_CLK_TCK` frequency. The current Android timestamp records framework
application binding, while Perfetto's `process_start_from_boot` records kernel process creation.
They need not agree within the matcher's 10 ms process-start tolerance, so valid profiles carrying
that metadata can be rejected. Profiles omitting process-start metadata do not take this comparison.
Widening the tolerance would not make these different events equivalent; a replacement needs focused
parsing/conversion tests and device verification against Perfetto metadata.

## Pending storage and restart recovery

Pending raw profiles live under `files/bitdrift_capture/crash_profiles/`, outside Android's
`files/profiling/` cleanup. Profile kind is encoded in SDK-owned filenames, so no separate sidecar
must survive a move. Borrowed copies are written to a temporary file, synced, and atomically
published before success is reported. Adoption syncs the renamed file and directories.

A fatal ANR or Java OOM profile can arrive before its report exists. Native attribution retains the
SDK-owned file with a retained suffix. On the next startup it waits for previous-run report
discovery and retries attribution once. An unmatched retry or an unreadable profile is deleted.
Newly imported files that were persisted before process death but not yet handed to native
attribution are also recovered. Matching and uploading require the corresponding runtime flag. The
SDK migrates legacy files carrying its retained suffix from the platform directory; it does not
sweep unrelated platform results.

Startup migration and pending replay failures are logged independently and do not prevent
registration for new automatic results. Pending files remain available for recovery on a later
listener start or process restart.

Each raw profile is bounded by the native uploader's `attachment.max_bytes` runtime limit (5 MiB by
default, before compression). The private pending store has a separate aggregate admission budget,
including incomplete copies, controlled by `client_feature.android.crash_profiles.max_pending_bytes`
(64 MiB by default). Both limits are read on each admission. Imports that exceed either limit fail
explicitly; existing files are not evicted when the aggregate budget is reduced. If a native
retention rename makes an entry disappear during a scan or expiry removal, admission fails rather
than undercounting its bytes. Pending files older than seven days are removed when the store is
scanned at startup or import, including while collection is disabled. This is opportunistic cleanup,
not a background timer. Upload-store limits and cleanup apply separately after attribution.
