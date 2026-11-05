# Cell types

`scripts/make_workflow.py` and `scripts/run_cell.py` both read the `CELLS` table in
`scripts/common.py`, so defaults stay in one place. This page explains the choices.

| type | command (selector) | arguments | trigger button | wait after trigger | on-device copy |
|---|---|---|---|---|---|
| `heap_dump` | registered `java_heap_dump_demo` | `buffer_size_kb=65536` | Log Single Message | 90 s | yes |
| `heap_profile_native` | registered `heap_profile_demo` | `duration_ms=5000`, `buffer_size_kb=8192`, `sampling_interval_bytes=65536`, `track_java_allocations=false` | Log JSON Field | 45 s | yes |
| `heap_profile_java` | registered `heap_profile_demo` | same, `track_java_allocations=true` | Log JSON Field | 45 s | yes |
| `stack_sampling` | built-in type `3` (`STACK_SAMPLING_TRACE`) | `duration_ms=5000`, `frequency_hz=100`, `buffer_size_kb=1024` | Log JSON Field | 45 s | **no** |

All argument types matter: integers are `sint_data` strings, booleans are `bool_data`, and strings
are `string_data`. `heap_profile_demo` and `java_heap_dump_demo` require every argument, and a
missing or mistyped one fails the command with `invalid_arguments`. The built-in stack sampler
requires `duration_ms`, `frequency_hz` and `buffer_size_kb`; the platform clamps each to its own
bounds.

## Command definitions in the account

The app's code is not what the backend validates. A workflow's arguments are checked against the
command's definition in the bitdrift account (`bd command list`), and `bd workflow create` rejects
any argument it doesn't list. `scripts/check_commands.py` compares the cells you plan with those
definitions, and checks that each command is enabled.

- The demo commands (`heap_profile_demo`, `java_heap_dump_demo`) are registered per account. If an
  argument is missing from the definition, re-registering is the user's call; the script prints the
  command. `--drop-arg` is the fallback.
- The stack sampler is a built-in (well-known type 3, key `stack_sampling_trace`). Its definition is
  fixed and it only needs to be enabled; it was enabled on staging in November 2026.

The test app's registered `system_trace` command also captures stack samples, but it isn't used
here. The built-in is the path customers' workflows use.

## Triggers

The gradle-test-app's **SDK APIs** tab has a Testing Tools card:

- **Log Single Message** logs `Log message with level: <level>`. Match it with REGEX
  `^Log message with level: .*$`.
- **Log JSON Field** logs `JSON field to match upon.`. Match it with EQUAL.

Any LIVE workflow in the account that matches the same log for this app fires as well. Before
running, list the app's live workflows (`bd workflow list -ojson`, then `bd workflow describe` for
candidates) and compare their first-step matchers with the cell's trigger. Overlapping workflows
that run a profiling command compete for the platform profiler: Android runs one profiling session
per app at a time. Propose pausing them for the duration of the test, then restore them.

Each temp workflow has a 30 s `minimum_execution_interval` (10 s for profiles), so a stray second
tap doesn't start another capture.

## Which button for which cell

`run_cell.py` taps the button from the `CELLS` table. If you need two cells that use the same
button to coexist, run them one after another and stop the first cell's workflow before deploying
the next.

## Why each cell exists

- **Heap dump** has no frames, only the heap graph. Correctness means class names
  (`heap_graph_class.deobfuscated_name`) and reference field names
  (`heap_graph_reference.deobfuscated_field_name`) restored from the mapping.
- **Heap profile, Java allocations off** records native allocation callstacks. Java frames still
  appear when ART code is on the stack (`base.odex`, `base.apk`, JIT cache). This is the cell for
  native symbolication (`libcapture.so`, `libgradletestapp_native.so`).
- **Heap profile, Java allocations on** adds Java allocation sampling. Most frames are Java, and
  many run from the JIT code cache. This is the most sensitive cell for method deobfuscation.
- **Stack sampling** records CPU perf samples (`perf_sample`), not allocations, through the SDK's
  built-in command (the production path). Frames go through the same symbolication and
  deobfuscation path. The built-in command doesn't go through `SampleCommands`, so there's no
  on-device copy. Integrity is the attachment's reported `size_bytes` = raw download = trace inside
  the TAR. On a root emulator you can add a device-side hash (see `devices.md`).

## JIT frames

Java frames in a profile come from one of three places, visible in `stack_profile_mapping.name`:

- `.../oat/arm64/base.odex` or `.vdex`: AOT-compiled app code
- `.../base.apk`: interpreted code
- `/memfd:jit-cache` or `dalvik-jit-code-cache`: JIT-compiled code

After an install or update, the app runs mostly interpreted and JIT-compiled code. Background
dexopt later AOT-compiles hot code, and JIT frames disappear from new traces. To test JIT
deobfuscation, reset compilation before the run:

```bash
adb shell cmd package compile -m verify -f io.bitdrift.gradletestapp
```

Then check the device actually ran JIT code. The verifier prints `jit_frames`, and 0 means the cell
didn't exercise that path.
