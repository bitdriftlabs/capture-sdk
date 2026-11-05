# Real device vs emulator (with and without root)

Run `python3 $SK/scripts/device_info.py --out <run>/device.json` first. It reads build properties,
classifies the target and lists what applies. Record the class in the report's environment line,
because it changes what a result means.

## What every target needs

- **Android 15+ (API 35+).** The SDK captures through `ProfilingManager`. Below API 35 every
  profiling command fails, and that is not a pipeline bug. Pick another target.
- **An app ABI you can symbolize.** Build for the target's ABI (`ro.product.cpu.abi`). Physical
  phones and emulators on Apple Silicon are `arm64-v8a`; emulators on x86 hosts are `x86_64`. For
  another ABI:
  - build with `-Prust-target=<abi>`;
  - take that ABI's debug files from `bazel-bin/<abi>/` and the test-app's
    `bazel-bin/platform/jvm/gradle-test-app/src/main/cpp/<abi>/`;
  - pass `--abi <abi>` to `apk_build_ids.py`.

  Release mappings are ABI-independent.
- **The app is `profileable`** (the test app is), so `ProfilingManager` can profile release builds
  without root.

## Real device (`real-device`, `user` build)

The target that matters most, because it is what customers run.

- **No root.** `run-as` works only on debug builds; release builds refuse it ("package not
  debuggable"). Check SDK prefs on release by behavior (see `device.md`). Don't try to work around
  it with exploits or unlock tools.
- **Background dexopt** AOT-compiles the app after some idle and charging time, and JIT frames
  disappear from traces. Reset with `cmd package compile -m verify -f <pkg>` right before each
  profile cell.
- **Screen and power.** A locked or dozing screen stalls the UI driver and delays uploads. Wake it
  (`run_cell.py` sends KEYCODE_WAKEUP), and ask the user to disable the lock screen or keep the
  device plugged in for long matrices. `svc power stayon true` is a device setting: ask, and restore
  with `svc power stayon false`.
- **Rate limiter.** The skill disables it for the run and restores it afterwards (SKILL.md step 2
  and step 7). `device_config` values can be reset by a server sync or a reboot, so recheck it if a
  later cell gets a profiling rejection mid-run. The flag is `profiling_testing/rate_limiter.disabled`; the
  `profiling` namespace refuses it on user builds.
- **Perf hardening** (`security.perf_harden=1`) is normal on user builds. `ProfilingManager` and
  traced_perf work with it; only direct `simpleperf` from the shell is restricted.
- **Vendor libraries** (`/vendor/...`) never symbolize: no debug files exist for them. Expected.

## Emulator without root (`emulator-nonroot`, Google Play images)

Behaves like a real device for permissions, with emulator-specific limits:

- **No root, same `run-as` rules** as a real device.
- **Stack sampling can be weak.** Hardware perf counters may be unavailable, and sampling falls
  back to a software clock or fails. If the built-in command returns a profiling error, or the trace
  has very few samples, report it as an environment limit and confirm the cell on a real device.
  Heap dumps and heap profiles work normally.
- **Slower and quieter.** Less JIT activity and fewer frames in short profiles. Lengthen
  `duration_ms` (`make_workflow.py --arg duration_ms=10000`) instead of drawing conclusions from a
  thin trace.
- **Network goes through the host.** Uploads to staging work, but a corporate VPN on the host can
  block them.

## Emulator with root (`emulator-root`, Google APIs / AOSP `userdebug` images)

Root is possible but off by default. `adb root` restarts adbd as root. Ask before using it, and run
`adb unroot` at the end. What it adds:

- **Prefs on release builds.** `adb shell cat /data/data/io.bitdrift.gradletestapp/shared_prefs/io.bitdrift.gradletestapp_preferences.xml`
  works regardless of debuggable. Still never print the key: grep for the key names only.
- **App-private files.** You can pull a profiler's output from the app's data directory, if it is
  still there, for an extra device-side integrity check on built-in commands, which have no hook
  copy.
- **Fewer surprises from device_config.** `put` and `set_sync_disabled_for_tests persistent` behave
  predictably.

Root doesn't make results more representative: `userdebug` builds differ from what users run
(debuggable system, different ART defaults). Treat a root emulator as a convenience for
inspection. A finding that matters for customers should be confirmed on a real device or a `user`
image.

## Rooted physical device (`real-device-rooted`)

Rare. Treat it like a root emulator for inspection, and like a real device for conclusions. Don't
root a user's phone to make the test easier.

## How to report the target

Put `class · model · Android version (API) · ABI · build type` in the report's environment line. For
anything that failed or looked thin, say whether the target class can explain it, with one of the
reasons above. Otherwise readers can't tell a pipeline bug from an emulator limit.
