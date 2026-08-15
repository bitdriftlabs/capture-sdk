---
name: android-launch-run
description: >-
  Build, install, launch, background, and kill the capture-sdk Android test apps on every connected
  device or emulator, put those devices into specific power/network states, and capture and parse
  logcat to answer questions about SDK runtime behaviour. Use this whenever the user wants to run an
  Android app on a device or emulator, reproduce behaviour under Android restrictions (doze, battery
  saver, Data Saver, standby buckets, cached-app freezer, airplane mode, background-restricted),
  verify what the SDK does on backgrounding or process death, compare an emulator against real
  hardware, or read bitdrift Rust logs (bd_client_stats, bd_logger, bd_api, bd_buffer) out of
  logcat. Reach for it for anything involving adb device state, RUST_LOG levels via
  debug.bitdrift.internal_rust_log, lifecycle timing, or flush/upload verification — including
  questions phrased as "does X still happen when the app is backgrounded?" or "why didn't the
  upload go out?", even when adb and logcat are never mentioned.
---

# Android launch & run

Drive real devices and emulators to observe what the SDK actually does at runtime, rather than
inferring it from source. The core loop is always the same:

**pick devices → set log levels → apply device state → launch → act → capture → parse**

Timing is usually the answer. Most interesting questions here ("did the flush finish before the OS
cut the network?") are races, so every timestamp must come from the **device clock**. Never compare
a host-side `date` against a logcat timestamp; they drift by seconds.

## Quick start

```bash
S=.claude/skills/android-launch-run/scripts

python3 $S/adbctl.py devices                        # what's connected
python3 $S/adbctl.py install                        # build + install debug app everywhere
python3 $S/adbctl.py run --scenario background-home # launch, background, capture, parse
```

Run one declarative scenario across all devices in parallel:

```bash
python3 $S/run_scenario.py assets/scenarios/minimal-background.json --out /tmp/run1
```

Parse a capture you already have:

```bash
python3 $S/parse_logs.py /tmp/run1/<device>/logcat.txt --ref "process ON_STOP"
```

## Which app

Defaults to `gradle-test-app` (`io.bitdrift.gradletestapp`). Override with `--app`:

| `--app` | Package | Notes |
|---|---|---|
| `gradle-test-app` (default) | `io.bitdrift.gradletestapp` | Debuggable; has the diagnostics UI and `bitdrift-lifecycle` logging |
| `gradle-tv-test-app` | `io.bitdrift.gradletvtestapp` | Android TV |
| `examples/android` | see module | Built with Bazel, not Gradle |

Install uses Gradle, which invokes Bazel for the Rust `.so`:

```bash
ANDROID_SERIAL=<serial> ./platform/jvm/gradlew -p platform/jvm :gradle-test-app:installDebug
```

`rust-target` defaults to `arm64`, correct for most phones and Apple-silicon emulators. Pass
`-Prust-target=x86_64` for an x86 emulator. First build is slow (Bazel builds the Rust lib);
subsequent ones are ~1–2 min.

**The app must be debuggable** or the Rust log property is ignored — see below.

## Setting Rust log levels

The SDK reads a system property at startup and converts it to `RUST_LOG`
(`Capture.setUpInternalLogging`), and only in debuggable builds:

```bash
adb -s <serial> shell setprop debug.bitdrift.internal_rust_log 'info,bd_client_stats=debug'
```

Set it **before launching** — it is read once during native library init. It survives until
reboot. Standard `RUST_LOG` filter syntax applies, and module prefixes work
(`bd_client_stats=debug` also enables `bd_client_stats::file_manager`).

Pick the narrowest filter that answers the question; `bd=trace` is enormous and will flood the
buffer, evicting the lines you care about.

| Question | Filter |
|---|---|
| Stats flush + disk write | `info,bd_client_stats=debug` |
| …plus *who* triggered the flush | `info,bd_client_stats=debug,bd_logger=debug` |
| …plus why an upload failed | add `,bd_api=debug` |
| Log (not stats) uploads | `info,bd_logger=debug` |
| Ring buffer to disk | `info,bd_buffer::ring_buffer=debug` |

## Reading the logs

Four independent sources, all on the device clock, so they interleave correctly in one capture:

| Source | Buffer | Gives |
|---|---|---|
| `bitdrift-lifecycle` (INFO) | main | **process** lifecycle + window focus, from the test app |
| `wm_*`, `input_focus`, `am_freeze` | events | OS-side activity lifecycle, focus, freeze |
| `bd_*::*` (Rust) | main | SDK internals; tag is the full Rust module path |
| `dumpsys netpolicy` | — | when the OS revoked network (post-run query) |

Capture both buffers together — `-b main,events` — or the OS events and app logs can't be correlated.

**`process ON_STOP` is usually the reference point.** It is what the SDK's background flush hangs
off, it fires ~700ms after the activity stops (`ProcessLifecycleOwner` debounces), and it lands
*before* the OS's own `wm_on_stop_called`. Measured on a Pixel 10:

```
17:28:55.912  ANDROID-RUN: ACTION home
17:28:57.178  bitdrift-lifecycle: process ON_STOP              <- reference
17:28:57.182  bd_logger::logger: state flushing initiated         +4ms   [platform asked]
17:28:57.218  bd_client_stats::stats: flushing collected stats…  +40ms
17:28:57.234  bd_client_stats::file_manager: writing snapshot    +56ms   <- disk write
17:28:57.241  bd_client_stats::stats: prepared … stats upload    +63ms   <- enqueued
17:28:58.215  bd_client_stats::stats: … upload completed …     +1037ms   <- acked
        …     [netpolicy] background-allow revoked            +4.99s   <- network cut
```

(Pixel 10, shared-core `c3ba1cba`. On revs up to `42637e1f` the stats wording differs — see
`references/log-signatures.md`.)

For what each Rust line means — and which are safe to draw conclusions from — read
**`references/log-signatures.md`**. Two traps worth knowing before you interpret anything:

- **`state flushing initiated`** is the only durable marker of a *platform-triggered* flush.
  `flush_state` is reachable only from a platform bridge, so its presence means Kotlin asked;
  its absence means an internal timer did.
- Log text depends on the **shared-core rev pinned in `Cargo.toml`**, not on any local
  `../shared-core` checkout, and it **does change across revs** — the `c3ba1cba` bump renamed most
  of the stats messages. `parse_logs.py` matches known old and new wordings, but after any
  shared-core bump, treat a *negative* result (no upload, no flush) as unproven until you have
  confirmed the signature still exists in the raw capture. A stale pattern and an absent behaviour
  look identical in the summary; the first run against `c3ba1cba` reported "zero uploads on both
  devices" and was pure pattern rot.

## Device states

Nine states are supported, each with verified setup, teardown, and a *verify* command — because
several fail silently:

```bash
python3 $S/adbctl.py mode battery-saver --on
python3 $S/adbctl.py mode doze-deep --on
python3 $S/adbctl.py mode reset          # everything back to default
```

`airplane` · `battery-saver` · `doze-deep` · `doze-light` · `data-saver` · `standby-restricted` ·
`bg-restricted` · `freezer` · `idle-allowlist`

**Read `references/device-modes.md` before using any of them.** Ordering matters more than it
looks: `standby-restricted` is silently reset by launching the app, `data-saver` does nothing
unless the network is first marked metered, and `doze` can only be forced after the screen is off.
The reference documents which modes must be applied *before* launch and which *after*
backgrounding, with the evidence for each.

## Actions

```bash
python3 $S/adbctl.py action home|back|recents|screen-off|wake|kill|force-stop|freeze|crash-bg
python3 $S/adbctl.py action wait --seconds 35
```

`home`, `back`, `recents` and `screen-off` are all different, and not interchangeable:

- **`recents`** does not fire `ON_STOP` at all — the activity stays "started" while the overview is
  open, so no backgrounding work runs. If a scenario appears to do nothing, check for this first.
- **`back`** revokes network sooner than `home` (~3.8s vs ~5.0s), so it shortens any race.
- **`screen-off`** re-locks a phone with a secure keyguard, which invalidates every later run.

## Marking actions in the log

Actions are stamped into logcat on the device clock so they appear inline in the timeline:

```bash
adb -s <serial> shell log -p i -t ANDROID-RUN "ACTION home"
```

`adbctl.py` does this automatically. Do it manually if you're driving adb yourself — otherwise
you'll be reduced to guessing when the action landed relative to what the app did.

## Multi-device

`adbctl.py` and `run_scenario.py` target **every connected device** by default, in parallel, and
write per-device output directories. Use `--serial <id>` for one device, or `--sequential` when
the numbers matter: parallel runs add tens of ms of adb and logcat jitter, which is fine for
pass/fail but can distort sub-second measurements.

Physical devices and emulators frequently disagree. That is a finding, not a bug — check
`references/flush-matrix.md` for cases where they diverged and why.

## Guard rails that keep runs valid

These come from runs that produced clean-looking but meaningless data:

1. **A locked device invalidates everything.** The app launches behind the keyguard and
   backgrounds itself during any foreground wait, so the action lands on an already-backgrounded
   app. `adbctl.py` aborts when `deviceLocked=1`. If the device has a secure lock, ask the user to
   unlock it; do not attempt to bypass it.
2. **Screen-off scenarios run last**, since each one re-locks the phone.
3. **Check for premature `process ON_STOP`.** If it appears *before* the action marker, discard the
   run. `parse_logs.py` flags this automatically.
4. **Always restore device state**, even after a failure. A device left in airplane mode or
   battery saver silently corrupts the next run — and it is someone's actual phone.
5. **Before a full sweep on a physical phone, ask the user to disable auto-lock.** Every screen-off
   scenario re-locks it, and each re-lock costs a manual unlock mid-run. `svc power stayon true`
   does *not* help — an explicit `KEYCODE_SLEEP` still sleeps and re-locks.
6. **One locked device must not abort the sweep.** `run_scenario.py` skips it per-device and
   continues; an early version called `sys.exit()` and silently stopped *every other* device from
   running too.
7. **After fixing the parser, re-derive from the raw logcat.** Summaries written earlier
   (`state.json`, report tables) still encode the old bug. The capture is the only artifact that
   cannot be wrong.
8. **Verify a negative before believing it.** "No upload happened" and "my pattern stopped matching"
   look identical in a summary. That ambiguity has produced both a false regression report (stale
   patterns after a shared-core bump) and a false pass (missing reference event). Check the raw
   capture for the *shape* of what happened.

## Reference files

Read these as needed rather than upfront:

- **`references/log-signatures.md`** — every log line worth matching, by subsystem, with what it
  proves and what it doesn't. Read before interpreting any capture.
- **`references/device-modes.md`** — the nine states: setup, teardown, verification, when each must
  be applied, and the silent-failure traps.
- **`references/flush-matrix.md`** — a worked 14-scenario study of stats flushing on backgrounding,
  with results from an emulator and a Pixel 10. Read it as a template for structuring a
  multi-scenario investigation, or when the question is specifically about flush/upload behaviour.

## What the parser reports

`parse_logs.py` prints a timeline then a three-part verdict, split so a foreground success can never
be mistaken for a backgrounding one:

```
FOREGROUND (before reference): 3 snapshot write(s) · 1 upload(s) enqueued, 2 acked
BACKGROUND (after reference):  snapshot written: YES · platform flush: 1 · forced: 2 · …
  upload ENQ/OK · ack 974ms
DISK-FLUSH DEBOUNCE (1000ms window): 5 opened · 5 closed empty · 0 coalesced ·
  5 write(s), closest 1918ms apart => invariant HELD
```

`upload` is `ENQ` / `DEBO` / `NONE`. **`DEBO` means the run measured the debounce, not the
question** — idle longer in the foreground and repeat.

The debounce line validates the `c3ba1cba` disk-flush window by the invariant it implies
(consecutive writes never closer than the window), because forcing an actual coalesce needs two
flushes under 1s apart and isn't reliably schedulable. When `coalesced` is 0 the coalescing path is
untested and the output says so — don't read `HELD` as full coverage.

## Scenario files

`assets/scenarios/*.json` describe runs declaratively so they're repeatable and diffable:

```json
{
  "name": "background-home",
  "rust_log": "info,bd_client_stats=debug,bd_logger=debug",
  "modes_before_launch": [],
  "steps": [
    {"action": "launch"},
    {"action": "wait", "seconds": 35, "why": "clear the 30s stats upload debounce"},
    {"action": "home"},
    {"action": "wait", "seconds": 40}
  ],
  "reference_event": "process ON_STOP"
}
```

Bundled scenarios:

| File | Purpose |
|---|---|
| `minimal-background.json` | Baseline: launch → clear debounce → HOME → observe |
| `background-doze.json` | Same, with deep doze forced right after screen-off |
| `kill-after-background.json` | Background, then kill the process 5s later |
| `background-no-wait.json` | **Diagnostic**: deliberately skips the debounce wait. Use it to confirm the minimum-upload-interval gate is still active after a shared-core bump — it should report `DEBO`. |
| `matrix/T01…T14.json` | The full 14-scenario matrix (backgrounding method · restriction modes · exit variants). Run T04/T06/T07 **last** — they use screen-off and re-lock a physical phone. Results and rationale in `references/flush-matrix.md`. |

Add a `why` to any non-obvious wait. A future reader — including you — will otherwise assume it's
arbitrary and shorten it, which is how the 30s debounce silently invalidated a whole matrix.
