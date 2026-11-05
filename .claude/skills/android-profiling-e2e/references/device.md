# Device, build, install and symbol uploads

## SDK key and API URL

The gradle-test-app reads its SDK key and API URL from default SharedPreferences
(`shared_prefs/io.bitdrift.gradletestapp_preferences.xml`, keys `api_key` and `apiUrl`). Uninstalling
the app wipes them, and then no command ever arrives. `adb install -r` keeps them.

**Debug builds** allow `run-as`, so check presence without printing values:

```bash
out=$(adb exec-out run-as io.bitdrift.gradletestapp cat shared_prefs/io.bitdrift.gradletestapp_preferences.xml 2>&1)
case "$out" in
  *"not debuggable"*|*"run-as:"*) echo "run-as refused (release build): check via the app instead" ;;
  *) echo "$out" | grep -oE 'name="(api_key|apiUrl)"' | sort -u | tr '\n' ' '; echo ;;
esac
```

**Release builds** refuse `run-as` ("package not debuggable"). `profileable` doesn't grant it. Check
the configuration through behavior instead: if the app was configured before and only reinstalled
with `install -r`, the prefs are still there. Otherwise open the app's settings screen and look at
the API URL field, or run the cell. A cell that never receives its command, with the workflow LIVE
and the trigger matched in logcat, points to missing prefs.

If the key is missing, ask the user for a file holding the SDK key (mode 600) and the API URL
(e.g. `https://api.bitdrift.dev` for staging). Then write the prefs with the app stopped. The key is
read inline so it never appears in the transcript:

```bash
adb shell am force-stop io.bitdrift.gradletestapp
KEY="$(cat <sdk-key-file>)"; URL="<api-url>"
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <string name="api_key">%s</string>\n    <string name="apiUrl">%s</string>\n</map>\n' "$KEY" "$URL" \
  | adb exec-in run-as io.bitdrift.gradletestapp sh -c 'mkdir -p shared_prefs && cat > shared_prefs/io.bitdrift.gradletestapp_preferences.xml'
unset KEY
```

If the file already holds other prefs, merge instead of overwriting: pull it through `run-as`, edit
the two entries locally, and push it back. This only works on a debug build. To configure a release
build, install the debug build first (same signing key), write the prefs, then `adb install -r`
the release APK on top. Or enter the key and URL in the app's settings screen.

## Version codes

Every build that gets debug files uploaded needs a version code that hasn't been used for this app.
The mapping is keyed by (app ID, version name, version code), and a reused code mixes mappings. The
gradle-test-app takes the code from an opt-in Gradle property (default 68):

```bash
./platform/jvm/gradlew -p platform/jvm :gradle-test-app:assembleRelease -Pe2eVersionCode=7102 -Pe2eTraceCopy=true
./platform/jvm/gradlew -p platform/jvm :gradle-test-app:assembleDebug   -Pe2eVersionCode=7101 -Pe2eTraceCopy=true
```

`bd debug-files list -ojson` shows which version codes already have a mapping
(`app_scopes[].app_build_id`).

`-Pe2eTraceCopy=true` sets `BuildConfig.E2E_TRACE_COPY`, which makes `SampleCommands.profileResult` write each uploaded trace to
`/sdcard/Android/data/io.bitdrift.gradletestapp/files/e2e-traces/` and log
`E2E trace copy path=... bytes=...`. `run_cell.py` pulls that copy for the integrity check. Built-in
SDK commands don't go through `SampleCommands`, so they have no copy.

## Build outputs to keep

Copy these into `.tmp/android-profiling-e2e/<run>/build/<variant>/` straight after each build:

| file | where | when |
|---|---|---|
| APK | `platform/jvm/gradle-test-app/build/outputs/apk/<variant>/gradle-test-app-<variant>.apk` | always |
| R8 mapping | `platform/jvm/gradle-test-app/build/outputs/mapping/release/mapping.txt` | release |
| libcapture debug info | `bazel-bin/arm64-v8a/arm64-v8a.debug.gz` (gunzip to `libcapture.so.debug`) | release |
| test-app native debug info | `bazel-bin/platform/jvm/gradle-test-app/src/main/cpp/arm64-v8a/arm64-v8a.debug.gz` (gunzip to `libgradletestapp_native.so.debug`) | release |

`bazel-bin` points at the output tree of the last Bazel config, so copy before anything else
builds. Confirm each debug file belongs to this APK by comparing build IDs (`file` prints
`BuildID[xxHash]` for libcapture and `BuildID[md5/uuid]` for the test-app library; both work).
`scripts/apk_build_ids.py` does this for you:

```bash
unzip -o -q <apk> 'lib/arm64-v8a/*.so' -d build/<variant>/apk_libs
for f in build/<variant>/apk_libs/lib/arm64-v8a/{libcapture,libgradletestapp_native}.so; do file "$f" | grep -o 'BuildID\[[^]]*\]=[0-9a-f]*'; done
file build/<variant>/dif/*.debug | grep -o 'BuildID\[[^]]*\]=[0-9a-f]*'
```

Debug builds keep a symbol table in the packaged `libcapture.so`, so no upload is needed. Native
frames carry names in the raw trace, and there's no R8 mapping.

## Uploads (release)

Use the API key file the user named (permission `DEBUG_FILE_UPLOAD`). `upload-proguard` ignores
`BD_API_KEY` in some versions, so pass `--api-key` explicitly:

```bash
bd debug-files upload-proguard --app-id io.bitdrift.gradletestapp --app-version 3.0 --version-code 7102 \
  --api-key "$(cat <api-key-file>)" build/release/mapping.txt
bd debug-files upload --api-key "$(cat <api-key-file>)" build/release/dif/
bd debug-files list --limit 100 -ojson  # wait for processing_state PROCESSED (no build IDs listed here)
```

Native debug files are keyed by build ID, so an identical library uploaded earlier still matches
("File already uploaded!"). An identical mapping is deduplicated too: the existing entry gains
another `app_scopes` version code instead of a new entry appearing.
Read `versionName` from `platform/jvm/gradle-test-app/build.gradle.kts` instead of assuming `3.0`.

## Install

```bash
adb install -r build/<variant>/gradle-test-app-<variant>.apk
adb shell dumpsys package io.bitdrift.gradletestapp | grep -E 'versionCode|versionName'
```

Debug and release are signed with the same key, so `install -r` switches between them and keeps
prefs, as long as each install has a higher version code than the installed one. Otherwise it fails
with `INSTALL_FAILED_VERSION_DOWNGRADE`. Plan version codes to rise in install order
(`device_info.py` prints the installed `app_version`). Don't `adb uninstall`: that wipes the prefs.

## Device state worth knowing

- `adb shell device_config get profiling rate_limiter.disabled`: with the limiter on, Android
  rejects repeated profiling requests. Disabling it is a device setting; ask first.
- `adb shell cmd package compile -m verify -f io.bitdrift.gradletestapp` puts the app back into
  JIT mode. See `cells.md`.
- Heap dumps need a large `buffer_size_kb`. The platform clamps the value to the device's
  `java_heap_dump_size_kb` bounds (`adb shell device_config get profiling java_heap_dump_size_kb_max`).
