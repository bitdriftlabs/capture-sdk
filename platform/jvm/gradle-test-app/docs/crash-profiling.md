# Manually testing Android crash profiles

This is a hands-on walkthrough: build and run the Gradle test app from Android Studio, click
its termination controls, and upload symbols with `bd`. It assumes an existing test backend
reachable over HTTPS and `bd` configured for the same environment/account. No automation
harness, local-universe setup, or root access is needed.

Use a test device/account: these steps deliberately hang and crash the sample. See the
[feature guide](../../../../docs/android-crash-profiling.md) for detailed behavior and known gaps.

## 1. Open and run the app in Android Studio

1. Open **`platform/jvm`** as the Android Studio project and let Gradle sync.
2. Select **JDK 17** for Gradle. In SDK Manager, install SDK platform **37**, Build Tools
   **37.0.0**, platform-tools, and NDK **27.2.12479018**.
3. Connect a physical device, enable USB debugging, and accept its authorization prompt.
   Use **Android 16 / API 36+ for ANR**, or **Android 17 / API 37+ for OOM**. Install available
   system/Mainline updates; the device also needs a Profiling module supporting these triggers.
4. Temporarily add these to [platform/jvm/gradle.properties](../../gradle.properties):

   ```properties
   e2eVersionCode=2026111001
   rust-target=arm64
   ```

   Choose an unused version code higher than the installed app's. Use that same code for the
   symbol upload below. These are local testing settings; do not commit them.
5. In **Build Variants**, choose **release** for `gradle-test-app`. Select that app's run
   configuration and your device, then click **Run**, not **Debug**.

Release enables R8 and strips native libraries, so it actually tests symbol recovery. The app
is already profileable. Debug is fine for a quick capture check, but readable debug names do
not prove symbolication. Do not mix Debug tasks into the release build.

For the few terminal commands below, work from the **capture-sdk repository root**, not
`platform/jvm` (Android Studio's terminal may initially open there):

```sh
adb devices -l
export SERIAL='your-device-serial'
export PKG=io.bitdrift.gradletestapp
export ABI="$(adb -s "$SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')"
```

Keep the screen unlocked and do not attach a debugger. No manual ART compilation settings are
required; leave them at their defaults.

## 2. Configure the sample

Open the app's **Settings**:

- Set the SDK **API key** and **API URL** for the test backend. Prefer its normal HTTPS endpoint
  for a physical device. Do not leave an unintended production URL selected.
- Turn on **Fatal Issue Reporter** and **ANR / OOM Crash Profiles**.
  Below API 36, the profiling switch rejects enablement with an error. On API 36, it shows
  **ANR profiles only**; OOM profiles require API 37+. The SDK also logs a warning when its
  startup configuration requests crash profiling on an unsupported Android version.
- Turn off **Deferred SDK Start**, simulated startup delay, and preferably session replay.
- Tap **Restart the App to apply changes**.

Wait for SDK initialization and configuration delivery. In Android Studio **Logcat**, filter
to the app and look for crash-profile collector/reconciliation messages or failures.
Startup success alone does not mean the asynchronous collector has registered yet.

The test backend must include the crash-profile ingestion, processing, and issue API changes.
It must enable `platform_events.enabled` and the relevant
`client_feature.android.crash_profiles.anr` / `.oom` flag. The profile flags default on within
the app's opt-in. Restart after changing runtime configuration.

For OOM tests, arrange sufficient temporary test budgets before restarting:

| Runtime setting | Example test value |
|---|---|
| `attachment.max_bytes` | 33554432 (32 MiB; the default is only 5 MiB) |
| `artifact_upload.buffer_byte_limit` | 67108864 (64 MiB; sampled at SDK startup) |
| `client_feature.android.crash_profiles.max_pending_bytes` | 67108864 (64 MiB) |

The backend's attachment ingress limit must agree. Its current Perfetto processing envelope is
32 MiB / eight million references, so larger captures may still be rejected. Do not assume the
separate chunked-upload work is deployed.

## 3. Enable Android's test background tracing

Do this **after the app is installed and the device has fully booted**. First run the two
`get` commands and note the original values so you can restore them:

```sh
adb -s "$SERIAL" shell device_config get profiling_testing system_triggered_profiling.testing_package_name
adb -s "$SERIAL" shell device_config get profiling_testing rate_limiter.disabled
adb -s "$SERIAL" shell device_config delete profiling_testing system_triggered_profiling.testing_package_name
sleep 3
adb -s "$SERIAL" shell device_config put profiling_testing system_triggered_profiling.testing_package_name "$PKG"
```

This targets developer background tracing at the sample; it does not replace the SDK opt-in.
Allow tracing to start before triggering a crash. Rearm it after an app update or reboot if
necessary. Starting tracing before installation can cause missing-package/redaction failures.
If the device rejects this setting, check its Profiling support rather than rooting it.

Start with normal rate limiting. If repeated tests are being rate-limited, temporarily use:

```sh
adb -s "$SERIAL" shell device_config put profiling_testing rate_limiter.disabled true
```

Record that the result used this override. Do not change another application's tracing setup
without coordinating with its owner.

## 4. Upload symbols with bd

Do this **after building/installing release and before triggering the test**. Use symbols from
that exact build; another build can change R8 output or Bazel's output-directory selection.

The R8 mapping is:

```text
platform/jvm/gradle-test-app/build/outputs/mapping/release/mapping.txt
```

Extract the two matching native debug files:

```sh
export SYMBOLS=".tmp/crash-profile-symbols"
mkdir -p "$SYMBOLS"
gzip -dc "bazel-bin/$ABI/$ABI.debug.gz" > "$SYMBOLS/libcapture.so.debug"
gzip -dc "bazel-bin/platform/jvm/gradle-test-app/src/main/cpp/$ABI/$ABI.debug.gz" \
  > "$SYMBOLS/libgradletestapp_native.so.debug"
```

Use an API key with `DEBUG_FILE_UPLOAD`, not the sample's SDK ingestion key. Replace the domain
and key-file path below. The base domain is not a full URL; check that the CLI's printed upload
URL points at the intended backend. Do not run these commands with shell tracing enabled.

```sh
export DOMAIN='your-test-base-domain'
export KEY_FILE='/secure/path/to/debug-upload-key'
export VERSION_CODE=2026111001
bd debug-files upload-proguard \
  --base-domain "$DOMAIN" --api-key "$(cat "$KEY_FILE")" \
  --app-id "$PKG" --app-version 3.0 --version-code "$VERSION_CODE" \
  platform/jvm/gradle-test-app/build/outputs/mapping/release/mapping.txt
bd debug-files upload \
  --base-domain "$DOMAIN" --api-key "$(cat "$KEY_FILE")" "$SYMBOLS"
bd debug-files list --limit 100 -ojson
```

Confirm the matching files are **PROCESSED**, not merely uploaded. Native files match by build
ID; R8 mappings match package/version/build. The version name is currently `3.0`; confirm it in
[the build](../build.gradle.kts). Upload again with a new version code after changing the build.

## 5. Click through the two crash cases

### Fatal ANR, API 36+

1. In **App Terminations**, select **ANR_BLOCKING_GET**, then **Force App Exit**.
2. Once the app hangs, tap it again to cause an input timeout. If needed, send input explicitly:

   ```sh
   adb -s "$SERIAL" shell input keyevent --async KEYCODE_ENTER
   ```

3. Wait for Android's ANR dialog. Leave it open briefly to allow profiling to finish; watch
   Logcat rather than repeatedly triggering the test. Then select **Close app**, not **Wait**.
4. Relaunch the app from its icon, without reinstalling or clearing storage. The new process
   recovers the report and retained profile. Leave it open while they upload.

Expected: an ANR occurrence with its associated system trace. Force-stop is **not** a substitute
for Android detecting and closing a real ANR.

### Java OOM, API 37+

1. Select **APP_CRASH_JVM_OOM**, then **Force App Exit**.
2. Wait for the retained allocations to exhaust the Java heap and crash the process. Do not
   substitute a manually thrown exception, native crash, or process kill.
3. Relaunch from the app icon and leave it open for recovery, delayed results, and uploads.

Expected: a JVM OOM occurrence with an associated ART Perfetto heap graph, not an HPROF file.

## 6. Check the result

Open the new occurrence in bitdrift. Confirm the right app/build and crash type, then check its
linked profile if the UI exposes it. Profile UI may lag backend support; in that case use `bd`:

```sh
bd issue group list --platform android --app-id "$PKG" --last 1h -ojson
bd issue list YOUR_GROUP_ID --last 1h -ojson
bd issue describe YOUR_GROUP_ID YOUR_REPORT_ID -ojson
```

Inspect `profiles.profiles` on the **specific occurrence**. Expect `processing_status=SUCCEEDED`
with both raw and processed outputs available. Retry the read for a few minutes if processing is
still catching up; an ordinary crash report or an empty profile section alone is not a pass.

If a profile download is available, open the processed bundle locally in Perfetto and check
actual timeline/heap data and recovered app names. Processing success alone does not prove
symbol recovery. Do not upload private recordings to a public web service.

### Diagnosing missing profiles

Keep both `BitdriftCapture` and `bd_artifact_upload` messages in Logcat. `RUST_LOG=bd=debug`
enables Rust diagnostics; it does not select the Kotlin `BitdriftCapture` messages.

Follow these stages:

1. **Collector reconciliation and listener registration:** check the local mode, fatal-reporting
   setting, enabled kinds, and whether source facts are available.
2. **System profiling result:** check the trigger type, error code/message, and path. A file in
   Android's profiling directory alone does not prove that the SDK received a successful callback.
3. **Admission and submission:** check raw size, attachment/pending budgets, ignored callback
   reasons, and the handoff to native attribution. Startup also logs pending-file resubmission.
4. **Matching:** each candidate logs its report/run ID, PID, boot count, and source timing.
   Rejections identify installation, kind, boot-epoch, process-start, lifetime, or missing-PID
   mismatches. PID-reuse ambiguity is an error. Observation summaries are limited to the first
   16 entries, but matching still checks all entries.
5. **Disposition and upload:** look for retention for the next process, deletion of an unmatched
   resubmission, or `queued crash profile PROFILE_ID for reports [...]`. Follow that **profile**
   UUID through upload intent, file upload, and success ACK; a report or state upload is separate.

For a quick negative control, turn crash profiling off, restart, and trigger
**APP_CRASH_REGULAR_JVM_EXCEPTION**. Ordinary crash reporting should still work without a profile.

## 7. Finish up

- Turn off **ANR / OOM Crash Profiles** and restart while platform events are still enabled.
  Check that SDK-owned triggers are removed; stopping the process alone does not remove them.
- Restore the two DeviceConfig values recorded above. If they were originally `null`, use:

  ```sh
  adb -s "$SERIAL" shell device_config delete profiling_testing system_triggered_profiling.testing_package_name
  adb -s "$SERIAL" shell device_config delete profiling_testing rate_limiter.disabled
  ```

  Otherwise use `device_config put profiling_testing KEY ORIGINAL_VALUE`.
- Restore temporary Gradle properties and backend limits. Keep keys, dumps, and symbols out of Git.

## Emulator or local-network differences

The same flow works with an API 36/37 emulator selected in Android Studio. Prefer ARM64 on
Apple Silicon; for x86_64, change `rust-target=x86_64` before building. Boot and install before
arming tracing.

For a host-local backend, the emulator URL is `http://10.0.2.2:8001`; this is the only cleartext
host the sample currently allows. It does not work on a physical phone, and `adb reverse` alone
does not change cleartext policy. Prefer an approved HTTPS endpoint for the phone and `bd`
uploads; the CLI's symbol upload helper uses HTTPS.

On API/target 37+, an endpoint on the local network also needs:

```sh
adb -s "$SERIAL" shell pm grant "$PKG" android.permission.ACCESS_LOCAL_NETWORK
```

Restore the prior grant afterward if you changed it. Public HTTPS endpoints do not need this
local-network grant. For missing profiles, check Logcat, tracing/rate limits, size budgets, and
the [known attribution gaps](../../../../docs/android-crash-profiling.md) before repeating crashes.
