---
name: android-profiling-e2e
description: >-
  Android only. Run end-to-end tests of the capture-sdk Android profiling pipeline (ProfilingManager,
  API 35+) on a real Android device or emulator:
  Java heap dumps, heap profiles (native-only or with Java allocations) and stack sampling traces,
  from the gradle-test-app through a temporary bitdrift workflow, the command attachment upload and
  backend processing (native symbolication, R8 deobfuscation including JIT frames) to the raw and
  processed downloads. It checks byte integrity, verifies every restored name against the exact
  mapping.txt and native debug files, compares the backend with Perfetto's own trace_processor as a baseline,
  and writes a findings report with a suggestion per gap. Use this whenever someone wants to test,
  validate, verify, smoke-test or reproduce heap dump / heap profile / stack sampling / perfetto trace
  capture, symbolication or deobfuscation end to end, check a backend or SDK profiling fix on staging,
  or produce a profiling e2e briefing, even if they only say "run the profiling tests", "try the heap
  dump on my phone" or "does deobfuscation work on release now". Not for iOS: there is no iOS
  equivalent of this pipeline in this skill.
---

# Android profiling e2e

**Platform: Android only.** Everything here relies on Android tooling: `adb`, the
`platform/jvm/gradle-test-app`, Android `ProfilingManager` (API 35+), ART/JIT, R8 `mapping.txt`
and Android NDK native libraries. Don't apply it to iOS apps. For general device control (power
states, lifecycle, logcat parsing) the sibling skill
[android-launch-run](../android-launch-run/SKILL.md) goes deeper; this skill only uses the bits it
needs.

Paths below are relative to the repo root. `SK=.claude/skills/android-profiling-e2e`, so the
scripts are `$SK/scripts/*.py`.

This skill tests one path end to end:

```
gradle-test-app button ─► temp workflow ─► SDK command (ProfilingManager) ─► attachment upload
      ─► backend processing (symbols.pb + deobfuscation.pb sidecars) ─► raw / processed downloads
```

and answers three questions for every run ("cell"):

1. **Integrity**: are the bytes the device produced the bytes the backend serves?
2. **Correctness**: is every native symbol and every deobfuscated Java class, field and method
   right, checked against the exact `mapping.txt` and debug files of that build?
3. **Baseline**: how does the backend compare with Perfetto's own `trace_processor` given the same
   inputs: what only bitdrift does (better or worse), and what is the same in both?

Scripts live in `scripts/`, deeper reference material in `references/`. Read a reference when its
step comes up, not upfront.

## Ground rules

- **Secrets.** Never print, echo or log an API key, SDK key or JWT. Read them inline from files
  (`--api-key "$(cat <file>)"`) and redact any output that could contain them. Don't create keys
  with `bd key create` on your own; ask the user for a key file.
- **One confirmation for outward-facing actions.** Uploading debug files, creating/deploying/stopping
  workflows and pausing someone's live workflows all touch a shared account. Present the plan
  (cells, version codes, uploads, workflows created, workflows paused) and get one explicit OK
  before doing any of it.
- **One run per cell.** Results are deterministic; repeats cost device time and add nothing. Repeat a
  cell only when it failed, looks anomalous, or the user asks.
- **Scratch goes in** `.tmp/android-profiling-e2e/<YYYYMMDD-HHMM>/` at the repo root (gitignored). Each cell
  gets `runs/<cell>/`.
- **Test builds use two opt-in Gradle properties** of the gradle-test-app. Normal builds don't set
  them and behave as before. `-Pe2eVersionCode=<code>` sets the version code;
  `-Pe2eTraceCopy=true` makes the demo commands keep an adb-pullable copy of each uploaded trace.
- **Prefer the bd CLI** (the `bd-cli` skill) over MCP tools for bitdrift operations.

## 1. Plan the matrix with the user

Ask for anything the user hasn't specified, in one round:

- **Trace types** (one or more):

  | cell type | command | what it exercises |
  |---|---|---|
  | `heap_dump` | `java_heap_dump_demo` | heap graph class + field deobfuscation |
  | `heap_profile_native` | `heap_profile_demo`, `track_java_allocations=false` | native symbolication, Java frames from ART unwinding |
  | `heap_profile_java` | `heap_profile_demo`, `track_java_allocations=true` | Java allocation frames, JIT deobfuscation |
  | `stack_sampling` | SDK built-in command type 3 (`STACK_SAMPLING_TRACE`) | CPU perf-sample frames, native and Java, through the production profiler path |

  Exact arguments, triggers and waits are in `references/cells.md`.
- **Build variants**: debug, release, or both. Release means R8 obfuscation plus stripped native
  libraries, so it needs the mapping and native debug files uploaded (step 3).
- **Target**: which `adb` serial, if several are attached, and what kind it is: a real device, an
  emulator without root, or an emulator with root. They differ in what you can inspect and in how
  far results generalize; see `references/devices.md`.

A cell is one (type, variant) pair, e.g. `release · heap_profile_java`. Name cells short and
stable, like `R-HPJ`, `D-HD`; they become directory names and report labels.

## 2. Preflight

Check these and tell the user what you need from them before starting. Fixing the environment
mid-run wastes device time.

```bash
adb devices                                         # exactly one device, or set ANDROID_SERIAL
python3 $SK/scripts/device_info.py --out <run>/device.json   # real device / emulator / root, API, ABI, notes
bd auth --status -ojson                             # authenticated; note which env ~/.bd/profile.yaml targets
python3 $SK/scripts/common.py --check               # trace_processor, llvm-symbolizer, zstd found
python3 $SK/scripts/check_commands.py --types <cell types, comma-separated>
python3 $SK/scripts/list_overlaps.py --types <cell types>
adb shell device_config get profiling_testing rate_limiter.disabled
```

- **Target class.** Act on `device_info.py`'s `notes`, and read `references/devices.md` for that
  class:
  - Below API 35 nothing can be profiled, so pick another target.
  - On a non-arm64 ABI, build and symbolize for that ABI.
  - On an emulator, treat thin or failed stack sampling as an environment limit.
  - Root (`adb root`) is available only on `userdebug` emulators. Ask before using it, and
    `adb unroot` at the end.
  - Findings that matter for customers should hold on a real device.
- **Commands in the account.** The backend validates a workflow's command arguments against the
  command's definition in the account, not against the app's code. `bd workflow create` fails with
  `unknown argument X for command Y` when they differ. `check_commands.py` checks every planned cell
  upfront:
  - Built-in commands (stack sampling) must be enabled. Enabling one is an account change, so ask
    first.
  - Registered demo commands must define every argument the cell sends. On a mismatch the script
    prints the `bd command register` fix (a shared account change that needs the user's OK).
    Otherwise use `--drop-arg` and say in the report what coverage was lost.
- **Interfering workflows.** `list_overlaps.py` scans the whole account (`bd workflow list` returns at
  most 100 per call) for LIVE workflows on this app. Those under `interfering` run a device command,
  so they compete for the profiler or fire on your trigger: propose stopping them, and snapshot each
  definition first with `bd workflow describe <id> -ojson > wf/<id>.orig.json`. The ones under
  `also_fire_harmless` only chart or capture, so ignore them.
  If the user doesn't want an interfering workflow stopped, move your cell to a trigger it doesn't
  react to: pass the same `--trigger "<button>"` to `make_workflow.py` and `run_cell.py`.
- **Rate limiter.** By default, disable it for the run and restore it afterwards: the user decided
  this is standard for this skill. Android's profiling rate limiter rejects back-to-back captures,
  which would make later cells fail for reasons unrelated to the pipeline.
  The flag lives in the `profiling_testing` namespace. The same key under `profiling` is refused
  on user builds (SecurityException) and has no effect, so don't use it.
  ```bash
  adb shell device_config get profiling_testing rate_limiter.disabled > <run>/rate_limiter.orig   # "null" = unset
  adb shell device_config put profiling_testing rate_limiter.disabled true
  adb shell device_config get profiling_testing rate_limiter.disabled                           # must read true
  ```
  With the limiter on, the defaults (`adb shell device_config list profiling`: every `cost_*` 10,
  `max_cost_process_1_hour` 10) allow one capture per hour.
  Restore it in step 7. Mention it in the plan you confirm with the user, as a device change you
  will undo.
- **Keys.** Release cells need an API key with `DEBUG_FILE_UPLOAD` permission, in a file the user
  names.
- **App configuration.** The app needs the SDK key and API URL in its prefs. `references/device.md`
  explains how to check them on debug and release builds without printing the key.

## 3. Build, install, upload

Follow `references/device.md`. In short:

1. Pick a unique version code per build (e.g. debug `7101`, release `7102`). Check
   `bd debug-files list --limit 100 -ojson` (`app_scopes[].app_build_id`) so you don't collide with
   an earlier upload.
2. Build with
   `./platform/jvm/gradlew -p platform/jvm :gradle-test-app:assemble<Variant> -Pe2eVersionCode=<code> -Pe2eTraceCopy=true`.
   Expect about 2 minutes when mostly up to date, and 10 minutes or more from cold.
3. Right after the build, copy the APK, `mapping.txt` (release) and native debug files (release) into
   `.tmp/.../build/<variant>/`. Bazel's `bazel-bin` symlink moves with the next build config.
   Then match each debug file to the packaged library by build ID:
   `python3 $SK/scripts/apk_build_ids.py <apk> --dif build/<variant>/dif --out build/<variant>/build_ids.json`.
   Old timestamps on the debug files are normal when Bazel was up to date. The build-ID match is
   what counts.
4. `adb install -r` (debug and release share a signing key, so prefs survive). Don't uninstall:
   that wipes the SDK key and URL.
5. Release only: upload the mapping (`bd debug-files upload-proguard`) and native debug files
   (`bd debug-files upload`), keeping their console output in `build/<variant>/upload_*.log`.
   - `bd debug-files list` shows no build IDs. The upload log's "Code ID" lines are where you
     confirm the uploaded files match `build_ids.json`.
   - An identical file is deduplicated: "File already uploaded!" for native files, or an extra
     `app_scope` on an existing mapping entry. That counts as success, not a failed upload.
   - Wait until `processing_state` is `PROCESSED`.

## 4. Temporary workflow per cell

```bash
python3 $SK/scripts/make_workflow.py --cell R-HPJ --type heap_profile_java --out .tmp/.../wf/
bd workflow create .tmp/.../wf/R-HPJ.workflow.json --metadata-file .tmp/.../wf/R-HPJ.metadata.json --deploy -ojson
```

The new workflow's ID is at `.create.id` in the output. Record it in `wf/created.json`. If
`check_commands.py` flagged arguments and the user didn't approve re-registering, add
`--drop-arg <name>` for each. One workflow per cell keeps arguments and results unambiguous. When the cell is done, **stop** it (`bd workflow stop <id>`); the user wants
temp workflows kept for auditing, not deleted.

## 5. Run each cell

- Heap profiles and stack sampling: before the run, reset the app's compilation state so Java code
  runs JIT-compiled, as it does after an install or update:
  `adb shell cmd package compile -m verify -f io.bitdrift.gradletestapp`. Without this, background
  dexopt may have AOT-compiled the app, and the trace has no JIT frames to test.
- Run the cell. It relaunches the app, waits for config, taps the trigger, generates UI activity
  during capture, waits for upload, and pulls the on-device copy:
  `python3 $SK/scripts/run_cell.py --cell R-HPJ --type heap_profile_java --out .tmp/.../runs`
- Post-process. This finds the session and attachment, downloads raw/processed/default plus
  `/status`, compares hashes and runs the trace_processor summaries:
  `python3 $SK/scripts/post_cell.py --cell R-HPJ --type heap_profile_java --runs .tmp/.../runs --workflow <id>`

If a run produces no command, check in this order: the workflow is LIVE, the app has the SDK
key/URL, the trigger log matched (logcat), and no other workflow grabbed the profiler. Fix the
cause, then rerun that cell once.

## 6. Verify and compare

Run what applies to the cell type; each script writes JSON beside the run and prints a summary.

| check | script | applies to |
|---|---|---|
| Java frames vs mapping, JIT frames, candidate ambiguity | `verify_java_frames.py <tar> <mapping>` | release heap profiles, stack sampling |
| Native frames vs `llvm-symbolizer` | `verify_native.py <tar> <build_id> <debug_file>`, once per app library, with IDs and files from `build_ids.json` | release heap profiles, stack sampling |
| Heap-graph classes and fields vs mapping | `verify_heap_dump.py <tar> <mapping>` | release heap dumps |
| Where `packet_loss_events` comes from | `count_dropped_flags.py <raw trace>` | every cell |
| trace_processor baseline: `trace_processor bundle`, same checks, frame-by-frame diff | `baseline_compare.py --cell-dir ... --type ... --mapping ... --symbols build/<variant>/dif` | every cell |

Native libraries stored uncompressed in the APK appear in the trace as a `.../base.apk` mapping with
the library's build ID, so look them up by build ID, not by name. A library with 0 frames didn't run
during the capture. That is a coverage note, not a failure.

For debug cells there's nothing to deobfuscate. Verify integrity, trace health and that names are
present, and skip the mapping checks.

Then use the Perfetto skill's memory and CPU workflows (`perfetto:perfetto`: `heap_dump.md`,
`java_allocation_profile.md`, `native_heap.md`, or `querying.md` for perf samples) on the processed
TAR. Use them for one or two qualitative observations, such as the top allocating or retaining app
code. These show whether the deobfuscated names make the trace usable, which counts alone can't.
`post_cell.py` already ran the perfetto skill's triage queries into `perfetto_triage.json`: the
dominator path for heap dumps, `triage_java_allocation.sql` / `triage_native_heap.sql` for heap
profiles, and `linux.perf.samples` with per-thread counts for stack sampling. Start there instead of
writing new SQL, and use the android-profiler skill's analysis workflows (perfetto-sql,
perfetto-trace-analysis) for anything deeper. `analysis_*.json` from `analyze_frames.py` covers only
naming: per-module coverage before and after processing. Its app coverage counts a frame only when
every candidate method is app code, and the `_any_candidate` figure is an upper bound that ambiguity
inflates.

How to read the backend `/status` diagnostics, and which known behaviors to expect, is in
`references/backend.md`. Classify every surprise as **bitdrift only**, **same in trace_processor**,
or **environment** (device class, see `references/devices.md`), using the baseline comparison.

## 7. Clean up

- Stop every temp workflow you created (keep them; don't delete).
- Redeploy any workflow you paused, then diff its `flows` and `actions` against the snapshot.
- Restore the rate limiter: if `rate_limiter.orig` was `null`, run
  `adb shell device_config delete profiling_testing rate_limiter.disabled`; otherwise `put` the saved
  value back. Then read it again to confirm.
- Tell the user about device state you changed: compile mode, rate limiter, installed version code,
  `adb root` (run `adb unroot`), stay-awake setting.

## 8. Report

Always write `report.md` in the run directory, using the structure and writing rules in
`references/report.md`. Publish an HTML briefing only if the user asks; the same reference covers
it. A first-time reader should understand the report without having seen this conversation. That
means conclusions rather than history, a concrete suggestion for every gap, and a link for every
piece of evidence.
