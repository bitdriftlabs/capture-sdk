# System-triggered ANR/OOM crash-profile acceptance

Use the existing Gradle sample and [device/lifecycle skill](../../android-launch-run/SKILL.md).
Exercise the SDK's real `CrashProfileCollector`, not an independent profiling listener. The former
sample-only trigger activity, registration implementation and `e2eTriggerProfiling` build property
have been removed. The sample's existing termination controls can generate the same ANR/OOM inputs
without competing for trigger ownership. Native results alone do not establish backend acceptance.

## Startup configuration and ownership

`Configuration.crashProfilingMode` defaults to **null** (disabled). Enable the sample's **ANR / OOM
Crash Profiles** configuration switch to select `CrashProfilingMode.AUTOMATIC`, enable **Fatal Issue
Reporter**, then restart. The
`client_feature.android.crash_profiles.{anr,oom}` runtime flags default true, but cannot override
local opt-out. ANR requires API 36+; Java OOM requires API 37+.

Opt-in grants the SDK exclusive ownership of each enabled trigger type, its result files and, for
ANR, the process-state summary. Multiple global listeners are supported, but trigger registrations
have no per-library owner: adding the same type replaces its configuration. Do not register another
ANR/OOM consumer or write the process-state summary alongside the SDK. Direct profiling continues
using request-specific callbacks and must be ignored by crash adoption.

Triggers persist across process restarts and reboots. Existing SDK preferences record claimed types
so local opt-out, fatal-reporting opt-out or runtime disable can remove only those types.
Unregistering a callback alone does not stop capture. Coordinate any ownership handoff with the
app's other profiling consumers; Android cannot identify a replacement registration's owner. Old
M0/POC installs predate these markers: remove their old registrations on a dedicated device or
uninstall the test app before testing fresh-install opt-out.

## Build and configure

Record the selected device, fingerprint, SDK level, Profiling Mainline package/version, boot count,
app version and pre-run profiling settings/processes. Use explicit serials and task-owned AVDs, or
obtain approval before changing a shared device. Preserve each APK and R8 mapping used in
restart/update tests.

```sh
export SERIAL=<explicit-adb-serial>
export OUT="$PWD/.tmp/android-profiling-e2e/<unique-run>"
mkdir -p "$OUT"
ANDROID_HOME=<android-sdk> ./platform/jvm/gradlew --no-daemon -p platform/jvm \
  :gradle-test-app:assembleRelease -Pe2eVersionCode=<unique-version-code>
adb -s "$SERIAL" install -r \
  platform/jvm/gradle-test-app/build/outputs/apk/release/gradle-test-app-release.apk
```

Run Debug unit tests in a separate Gradle invocation. A combined invocation containing a Debug task
enables the sample's Crashlytics plugin and can break release mapping-task setup without Google
Services. On API/target 37+, the local endpoint additionally requires the sample's declared
`android.permission.ACCESS_LOCAL_NETWORK` runtime permission; grant it explicitly on the owned
emulator and restore the prior state. Shell TCP access does not prove app access.

Start the app normally, configure its API key/URL and enable crash profiling, then restart. For an
offline SDK-only check, use a dummy key, `http://10.0.2.2:1` and replay disabled. This starts no
backend and must use no customer data. Inspect `bitdrift` logcat for trigger reconciliation and
`bd_artifact_upload` for report restoration, readiness and profile enqueue. The runtime flags remain
independently remotely controllable.

On an owned emulator, the developer background-tracing override is:

```sh
adb -s "$SERIAL" shell device_config put profiling_testing \
  system_triggered_profiling.testing_package_name io.bitdrift.gradletestapp
```

**Install first, boot completely, then start tracing.** A trace started before installation can lack
the package UID and fail redaction. After installation, delete this override, wait briefly, and set
it again to refresh the package snapshot. Reboot can require rearming it; distinguish fresh
callbacks from pre-reboot queued results. A long-running trace's package version can remain stale
after an APK update.

Record every override. `profiling_testing rate_limiter.disabled=true` is a labelled diagnostic
control, not ordinary-rate acceptance. Temporary unredacted files are diagnostic-only: never upload,
commit or count them as successful redacted delivery. On tested modules `370399999` and `361040400`,
`cmd profiling_service` has no shell implementation and `dumpsys profiling_service` supplies no
useful output; do not assume newer diagnostic commands.

## Drive existing sample controls

In **App Terminations**, select a specific reason and press **Force App Exit**:

| Check | Reason / evidence |
|---|---|
| Recovered ANR | `ANR_SLEEP_MAIN_THREAD`; allow the main-thread block to recover |
| Fatal ANR | `ANR_BLOCKING_GET`; close through Android's ANR dialog |
| Unhandled Java OOM | `APP_CRASH_JVM_OOM`; require actual retained allocations and a persisted JVM report |
| Direct-request coexistence | Use the existing stack-sampling profiler control; require one direct attachment and no crash adoption of its file |
| Restart/update/reboot | Preserve app data and original/receiving versions; inspect restored report candidates and linked envelopes |
| Local opt-out | Fresh install with runtime flags true must not alter triggers/summary or adopt files |
| Disable after opt-in | Restart with local opt-out or fatal reporting disabled; require removal of only previously claimed types |

For ANR, send actual input during the block, then allow input-dispatch timeout. Use `uiautomator
dump` to locate `android:id/aerr_wait` for recovery or `android:id/aerr_close` for a fatal ANR.
Use current accessibility bounds, wait until the block starts, then
`adb -s "$SERIAL" shell input keyevent --async KEYCODE_ENTER` to avoid a host-side input command
waiting for the hung app to exit. Verify the resulting OS timeout rather than assuming input delivery.
**Force-stop is not a fatal ANR.** Verify `ApplicationExitInfo.REASON_ANR` after the OS dialog
close. Resolve obfuscated handler names against the matching APK's R8 map.

Inspect `files/bitdrift_capture/report_uploads` on an owned debuggable/rooted device. Report source
facts are the typed `crash_profile_source` bytes in the report payload, not upload metadata. Report
IDs must agree across the report envelope, crash log, RAM candidate set and linked `crash_profile`
envelope. Exercise both restored queued reports and fresh asynchronous startup discovery; successful
upload ACK must not remove a RAM candidate.

A fatal ANR profile can reach the dying process before its report exists. Results are staged in
`files/bitdrift_capture/crash_profiles`; an unmatched live result there is renamed to
`*.bitdrift-retained-<kind>` and resubmitted once after the next process's report pass.
A still-unmatched resubmission is deleted. Preserve the live ANR original before closing the OS
dialog. Queued profiles in `report_uploads` are zlib-compressed originals; compare decompressed bytes
with raw downloads and the processed TAR's `trace.perfetto`. Verify actual report links, not observer
PID/session, filenames or host trace-processor run projections.

Keep capture source identity distinct from the receiving process and adoption-time lookup build
across updates/reboots. File survival alone is not proof of OS callback replay. Backend acceptance
additionally requires ClickHouse links, authorized reads, processing and independently importable
downloads through the monorepo's local-universe skill.

## Historical M0 recordings

`scripts/collect_triggers.py` and its tests remain for saved M0 evidence and reader/matcher parity.
They expect the retired experiment's external `trigger-profiling` files and JSONL history; **do not
run their live collection against the current sample**. Saved original bytes, startup lifetimes and
fictional report-ID fixtures remain useful offline evidence, but do not prove current SDK
restoration, canonical report links or backend authorization.

## Restore owned state

Turn off the sample's crash-profile configuration and restart to remove claimed triggers. Restore
every changed `profiling_testing` value to its recorded baseline (delete keys originally absent),
and verify cleanup. Stop only owned emulator/helper processes using recorded handles or exact PIDs;
delete only task-created AVDs. Preserve preexisting devices/apps and local dependency wiring. Keep
raw recordings, evidence and APK/mapping pairs local/ignored.
