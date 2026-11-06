# System-triggered ANR/OOM experiments

Use the existing Gradle sample and [device/lifecycle skill](../../android-launch-run/SKILL.md).
This is developer-only native evidence, not the command/workflow upload path. Production trigger
subscriptions, crash attachment uploads, and the production report/profile join are not implemented here.

## Select the experiment

| Question | Scenario / evidence |
|---|---|
| API 36/36.1 ANR | `enable`, then `recovered_anr` or `fatal_anr`; no OOM registration |
| API 37 ANR and unhandled Java OOM | `enable`, then the selected ANR scenario or `oom` |
| SDK default-handler survival during OOM | `offline_sdk`, then `oom`; inspect the actual persisted JVM report |
| Delayed results without terminating the process | `unlisten`, generate events, then `listen` |
| Process restart, update, or reboot | Preserve data, record the boundary, relaunch with `status`; distinguish these three cases |
| Direct-request coexistence | `direct`; require `direct_saved` and an `ignored_result` for trigger type 0 |
| Process-context association | Recovered then fatal ANR in one PID; restart delivery; another process's ANR containing the older PID; real OOM; update/reboot controls below |

Use a dedicated test app/device: registration and removal are app-wide by trigger type.
`disable` is not safe for an unrelated host's subscriptions. The experiment sets its trigger
cooldown to zero; that is not a production policy or proof of host ownership compatibility.

## Build and configure

From the SDK checkout, record the selected device, fingerprint, SDK level, Profiling Mainline
package/version, boot count, app version, and pre-run profiling settings/processes. Use explicit
serials and task-owned AVDs, or obtain approval before changing a shared device. Preserve the APK
and R8 mapping for every version used in restart/update tests.

```sh
export SERIAL=<explicit-adb-serial>
export OUT="$PWD/.tmp/android-profiling-e2e/<unique-run>"
export SK=.claude/skills/android-profiling-e2e
mkdir -p "$OUT"
ANDROID_HOME=<android-sdk> ./platform/jvm/gradlew --no-daemon -p platform/jvm \
  :gradle-test-app:assembleRelease :gradle-test-app:testReleaseUnitTest \
  -Pe2eTriggerProfiling=true -Pe2eVersionCode=<unique-version-code>
adb -s "$SERIAL" install -r \
  platform/jvm/gradle-test-app/build/outputs/apk/release/gradle-test-app-release.apk
```

The activity and launch hook are disabled by default; omit `e2eTriggerProfiling` to verify the
normal build. API 36 registers only ANR, API 37 also registers OOM. Do not equate the direct
stack-sampling profileability requirements with trigger availability: inspect the installed
manifest/profileability and actual trigger callback on the selected minified release build.

On an owned emulator, the developer background-tracing override is:

```sh
adb -s "$SERIAL" shell device_config put profiling_testing \
  system_triggered_profiling.testing_package_name io.bitdrift.gradletestapp
```

**Install first, boot completely, then start tracing.** A trace started before installation can
lack the package UID, causing `Redaction failed with error code: 1`. After installing, delete
this override, wait briefly, and set it again to refresh the package snapshot. A reboot can
also require rearming this developer override; inspect fresh callbacks separately from queued
pre-reboot results. A long-running trace's package version can remain stale after an APK update.

Record every testing override. `profiling_testing rate_limiter.disabled=true` bypasses global
limiting; use only for an explicitly labelled control, not ordinary-rate acceptance. Retaining
temporary unredacted files with `delete_temporary_results.disabled=true` is diagnostic-only.
Never upload, commit, or count an unredacted temporary file as successful redacted delivery.
On tested modules `370399999` and `361040400`, `cmd profiling_service` has no shell implementation
and `dumpsys profiling_service` supplies no useful output; do not assume newer diagnostic commands.

## Drive real events

```sh
adb -s "$SERIAL" shell am start -W -f 0x18000000 \
  -n io.bitdrift.gradletestapp/.diagnostics.profiling.TriggerProfilingActivity \
  --es scenario enable
```

Replace `enable` with a scenario from the table. `0x18000000` creates a new activity in the same
process; plain repeated `am start` did not reliably dispatch scenarios on the tested API 37 image.
Use `-S` only for an intentional force-stop/cold-start boundary. Confirm the `scenario` event in
`TriggerProfiling` logcat rather than trusting ActivityManager's successful launch response.

ANR scenarios post a main-thread block after 2.5 seconds. After four seconds, send an actual input
with `adb -s "$SERIAL" shell input tap 100 200`, then allow the input-dispatch timeout. The
recovered block returns after 15 seconds. If Android displays a dialog, use `uiautomator dump`
and locate `android:id/aerr_wait` for recovery or `android:id/aerr_close` for fatal ANR.
**Force-stop is not a fatal ANR.** Verify `ApplicationExitInfo.REASON_ANR` after an OS dialog close.

`oom` uses the sample's actual retained allocations until an unhandled Java OOM, not a manually
thrown exception or process kill. `offline_sdk` configures only this sample with a dummy key,
`http://10.0.2.2:1`, and replay disabled. It starts no backend and sends no customer data.
Resolve an obfuscated default-handler name against that APK's R8 map and inspect
`files/bitdrift_capture/report_uploads` on an owned debuggable/rooted device. A callback alone
does not prove an ordinary crash report survived.

For an update test, detach the listener, generate a real event, update the APK with `install -r`
and a different version code, then relaunch. Keep original and observer versions distinct.
For multiple pending results/reboot, record private-file existence separately from callbacks;
a file surviving reboot is not proof of callback replay. Do not repeat anomalous cells without
first inspecting their evidence.

## Collect and judge evidence

```sh
TRACE_PROCESSOR=<official-local-trace-processor> python3 "$SK/scripts/collect_triggers.py" \
  --serial "$SERIAL" --out "$OUT" --analyze
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s "$SK/scripts" -p test_collect_triggers.py -v
```

The collector validates UUID filenames, sizes and SHA-256, preserves separate deliveries even
when bytes match, and bounds its inventory to 16 files, 32 MiB/file and 4 MiB event history.
Use a fresh output directory per experiment. Analyze original bytes locally with the existing
`common.py` Perfetto integration; its bounded process/package/ANR queries flag overflow.
The app's history survives restarts/updates: record the pre-run baseline and do not count old
`profile_saved` rows rediscovered by collection as fresh callbacks.

The JSONL `observer` is the callback process, **never an inferred capture origin**. A fsynced
`launch` row records each run's PID/start clocks before inputs; a persisted experiment installation
UUID scopes history across restarts. This is sample-only identity storage, not a production SDK
device-identity implementation. Original OS basenames and bounded ANR trace-header prefixes are
retained for timing analysis.

With `--analyze`, each profile's `process_context` matches typed process/scheduler/heap PID
observations to compatible startup lifetimes in the same installation. REALTIME/BOOTTIME anchors
use a 1-second experimental boot-epoch allowance; missing/unstable clocks, unknown scope and query
overflow produce no matches. PID-specific scheduler/heap observation times are required; a
process-table row alone does not establish timing. This is not a universal boot-identity guarantee.
Several valid runs are allowed, and a recovered-ANR profile can match a later fatal ANR's process.
The receiving process's version is the initial lookup build; a stale package version or APK update
alone does not reject PID context. These are not exact-trigger attribution claims.

For the simplified matrix, detach the listener, recover an ANR, then cause a fatal ANR in the
same PID and close it through the OS dialog. Relaunch: both original profiles must match the old
startup PID, not the receiver. Another process's ANR should expose multiple legitimate run matches.
Confirm actual report PIDs by decoding the SDK's original `.cap` with the shared-core report
FlatBuffer schema. For OOM, verify the heap PID, platform fatal/exit and real JVM report; allow
bounded recovery after relaunch before declaring the report missing. JVM report PID metadata
remains unwired, so distinguish controlled source verification from a finished production join.

Queue another ANR, update with `install -r` and a new version, and verify old source PIDs with the
new receiver/lookup build. After reboot, reread the already SDK-owned profiles and journal;
that is not proof of OS pending-callback replay. Negative controls must reject another
installation, absent PID, incompatible boot epoch and reused/ended PID intervals. Label injected
PID-reuse controls honestly rather than claiming real kernel PID reuse.
Top-level `association: not_established` remains intentional: the collector projects process runs,
not canonical report IDs or production authorization.

The locked implementation plan moves actual matching into the SDK: retain dispatched ANR/OOM
report IDs with source evidence across launch/upload ACK, match locally, and upload each explicit
report/profile pair. Unmatched profiles stay local. M0's host trace processor is not that mobile
extractor. Later acceptance must inspect the durable pending entry and actual linked envelope,
exercise both local arrival orders and additional links to admitted bytes, then verify the
processing-path ClickHouse link and two-table authorized backend read through the monorepo's
existing local-universe skill. Do not substitute host run matches for these checks.

## Restore owned state

Run `disable` before stopping the test app, restore every changed `profiling_testing` value to
its recorded baseline (delete keys originally absent), and verify the preference/listener and
trigger-removal events. Stop only owned emulator/helper processes using their recorded process
handles or exact PIDs; delete only task-created AVDs. Preserve preexisting devices and apps.
Do not delete evidence or APK/mapping pairs needed to explain a failure; keep raw recordings
local/ignored unless an approved redistributable fixture has been explicitly selected.
