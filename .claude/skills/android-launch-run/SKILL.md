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

Observe what the SDK actually does at runtime instead of guessing. The core loop:

**pick devices → set log levels → apply device state → launch → act → capture → verify patterns → parse**

Two things make or break a run here.

**Timing is usually the answer.** Most interesting questions ("did the flush finish before the OS
cut the network?") are races, so every timestamp must come from the **device clock**. Never compare
a host-side `date` against a logcat timestamp; they drift by seconds.

**Runtime and source answer different halves of the question.** The capture tells you *what*
happened and *when*. The Kotlin tells you *why* and *where a fix would go*. Neither substitutes for
the other, and reading the relevant handler is cheap — usually one file. A run that reports "the app
switcher doesn't flush" is a fact; adding that `AppLifecycleListenerLogger` hangs the flush off
`ProcessLifecycleOwner`'s `ON_STOP` with no fallback turns it into a diagnosis with an obvious fix
site. Do the run, then spend two minutes in the handler before you write up a mechanism.

## Quick start

```bash
S=.claude/skills/android-launch-run/scripts     # run from the repo root
A=.claude/skills/android-launch-run/assets

python3 $S/adbctl.py devices                    # what's connected, and is anything locked
python3 $S/adbctl.py install                    # build + install debug app everywhere
python3 $S/run_scenario.py $A/scenarios/minimal-background.json --out /tmp/run1
```

`run_scenario.py` is the main entry point — it applies state, launches, acts, captures, and parses,
per device, and writes `<out>/<serial>/{logcat.txt, summary.txt, state.json}`. `adbctl.py` is for
driving one step at a time when no scenario fits.

Re-parse or re-check a capture you already have:

```bash
python3 $S/parse_logs.py       /tmp/run1/<serial>/logcat.txt --ref "process ON_STOP"
python3 $S/check_signatures.py /tmp/run1/<serial>/logcat.txt
```

## Before you trust any negative result: check the signatures

The most expensive mistake this skill can make is reporting "no upload happened" when the truth is
"my pattern stopped matching". Those are indistinguishable in a summary, and both a false regression
report and a false pass have already come from exactly that.

`check_signatures.py` settles it mechanically. It asks *can I still see this class of event at all?*
and prints `SEEN` / `UNSEEN` per family, then lists every `bd_*` line the parser did **not**
recognise, normalised and grouped:

```
  SEEN   stats-flush          FORCED=2 TICK=5 MERGE=5 SNAP=5 FLUSHED=2
  SEEN   stats-upload         PREP=0 ENQ=2 DISPATCH=0 ACK=3 RES=3
  UNSEEN stats-debounce       DEBOUNCE_OPEN=0 DEBOUNCE_SHUT=0 DEBOUNCE_COALESCE=0
```

An `UNSEEN` family means a zero elsewhere proves nothing — the detector itself may be dead. A
renamed message shows up in the unrecognised list immediately.

**Run it once per investigation, and always after a shared-core bump.** Log text is pinned by the
`rev` in `Cargo.toml`, not by any local `../shared-core` checkout, and it does change across revs —
one bump renamed most of the stats messages. Check what you're actually on:

```bash
grep -m1 'bd-client-common' Cargo.toml     # the rev every log wording follows
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

Set it **before launching** — it is read once during native library init. It survives until reboot,
so clear it when you're done. Standard `RUST_LOG` filter syntax applies, and module prefixes work
(`bd_client_stats=debug` also enables `bd_client_stats::file_manager`).

Pick the narrowest filter that answers the question; `bd=trace` is enormous and will flood the
buffer, evicting the lines you care about.

| Question | Filter |
|---|---|
| Stats flush + disk write | `info,bd_client_stats=debug` |
| **Did an upload actually land?** | `info,bd_client_stats=debug,bd_api=debug` |
| …plus *who* triggered the flush | add `,bd_logger=debug` |
| Log (not stats) uploads | `info,bd_logger=debug` |
| Ring buffer to disk | `info,bd_buffer::ring_buffer=debug` |

**Any delivery question needs `bd_api=debug`.** The server ack
(`received ack for stats upload "<id>", error: ""`) is logged at the transport layer, not by
`bd_client_stats` — so a stats-only filter hides the most direct evidence that the upload landed and
leaves you inferring delivery from the enqueue.

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
off, it fires ~700ms–1.3s after the activity stops (`ProcessLifecycleOwner` debounces), and it lands
*before* the OS's own `wm_on_stop_called`.

The shape is the same on both revs in circulation; **only the stats message text differs**, so match
the timeline to whichever rev `Cargo.toml` pins. Both are real HOME-button runs on a Pixel 10:

<details open>
<summary><b><code>c3ba1cba</code></b> — the <code>bump</code> branch (PR #1107)</summary>

```
      +0ms  process ON_STOP                                    <- reference
      +4ms  bd_logger::logger: state flushing initiated           [platform asked]
     +40ms  bd_client_stats::stats: flushing collected stats…
     +56ms  bd_client_stats::file_manager: writing snapshot       <- disk write
     +63ms  bd_client_stats::stats: prepared … stats upload       <- enqueued
   +1.037s  bd_client_stats::stats: … upload completed …          <- acked
    +4.99s  [netpolicy] background-allow revoked                  <- network cut
```
</details>

<details>
<summary><b><code>42637e1f</code></b> — <code>main</code></summary>

```
   -1.302s  >>> home
   -1.103s  OS: activity onPause
    -401ms  process ON_PAUSE
      +0ms  process ON_STOP                                    <- reference
      +3ms  bd_logger::logger: state flushing initiated           [platform asked]
     +18ms  bd_client_stats::file_manager: writing snapshot       <- disk write
     +25ms  bd_client_stats::stats: sending pending flush upload  <- enqueued
    +620ms  bd_api::api: received ack for stats upload            <- server acked
   +5.013s  api stream CLOSED: SocketException                    <- network cut
   +28.12s  writing snapshot … then upload DEBOUNCED              <- stranded on disk
```
</details>

Either way the flush wins the race against the network cutoff by roughly 5–8×. Note the last line of
the `42637e1f` run: later timed flushes keep writing to disk while backgrounded but cannot upload —
and there the SDK declined to even try, because of the minimum upload interval rather than the dead
network. Two different causes that look identical if you only watch for a missing upload.

### Two different intervals — don't conflate them

One log line names both, which is how they get mixed up:

```
stats disk flush interval 30s does not cleanly divide active upload interval 5s; falling back to upload cadence
```

| | What it is | Where it comes from |
|---|---|---|
| **`active upload interval`** (5s here) | the periodic upload *cadence* — how often an upload is attempted | derived; not the gate |
| **`stats.minimum_upload_interval_ms`** (30s) | anti-hammer *floor*: refuses any upload, periodic **or** ON_STOP flush, too soon after the last one | shared-core default; **not** in the runtime push |

**The foreground wait exists to clear the 30s floor, not the 5s cadence.** Seeing `5s` in that line
and shortening the wait is a natural mistake and it silently invalidates the run. Measured on
`42637e1f` — a periodic upload refused at 28.10s elapsed, the ON_STOP flush allowed at 35.06s, and on
an emulator a *flush* upload refused at 5.02s — bracketing the gate at 30s, its shared-core default.
The flag is not rev-specific, but re-confirm rather than assume: put `bd_runtime=debug` in the filter
and it logs every `updated value of <flag> to <value>`; a flag that never appears is at its default.

**The gate is anchored at enqueue and cleared on failure.** `last_flush_upload_time` is set when the
upload is handed off, and reset to `None` if that upload fails. So a run whose network is already
dead has *no* gate — the next flush upload sails through and reports `ENQ`, which looks like the gate
is short when really it was never armed. If a short-wait run unexpectedly shows `ENQ`, check whether
the preceding upload actually acked before concluding anything about the interval.

For what each Rust line means — and which are safe to draw conclusions from — read
**`references/log-signatures.md`**. One trap worth knowing upfront: **`state flushing initiated`** is
the only durable marker of a *platform-triggered* flush. `flush_state` is reachable only from a
platform bridge, so its presence means Kotlin asked; its absence means an internal timer did.

## Device states

Nine states, each with verified setup, teardown, and a *verify* command — because several fail
silently:

```bash
python3 $S/adbctl.py mode battery-saver --on
python3 $S/adbctl.py mode doze-deep --on
python3 $S/adbctl.py mode reset          # everything back to default
```

`airplane` · `battery-saver` · `doze-deep` · `doze-light` · `data-saver` · `standby-restricted` ·
`bg-restricted` · `freezer` · `idle-allowlist`

### Ordering is the whole game

**When a mode is applied decides whether the run answers the question at all.** This is the most
common way a technically-clean run turns out to have measured nothing:

- **A restriction applied *after* the flush already ran tests nothing.** Backgrounding alone kills
  network within ~5s and freezes the process within ~70s. Force doze a minute later and you will
  correctly observe "no uploads" — while having isolated nothing, because everything was already
  dead. Doze must be armed *before* `ON_STOP` to affect the flush-time upload.
- **`standby-restricted` is silently reset by launching the app**, so it must be applied after.
- **`data-saver` does nothing** unless the network is first marked metered.
- **`doze` can only be forced after the screen is off** and the device is unplugged.

Use `modes_before_launch` for anything that must be in force when the flush runs, and a `mode` step
for anything that must land after. **Read `references/device-modes.md` before using any of them** —
it documents which is which, with the evidence.

Always pair a restriction with an unrestricted control run. "No upload under doze" only means
something next to "upload acked in 700ms without it".

## Actions

```bash
python3 $S/adbctl.py action launch|home|back|recents|screen-off|wake|kill|force-stop|freeze|unfreeze
python3 $S/adbctl.py action wait --seconds 35
```

`home`, `back`, `recents` and `screen-off` are all different, and not interchangeable:

- **`recents`** does not fire `ON_STOP` at all — the activity stays "started" while the overview is
  open (the carousel renders a live tile, so the OS still reports it visible), so no backgrounding
  work runs. If a scenario appears to do nothing, check for this first. Moving on to another app
  *does* fire `ON_STOP`, so it defers the flush rather than losing it.
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
write per-device output directories. Use `--serial <id>` for one device, or `--sequential` when the
numbers matter: parallel runs add tens of ms of adb and logcat jitter, which is fine for pass/fail
but can distort sub-second measurements.

On `adbctl.py`, `--serial`, `--app` and `--sequential` are **global** flags and must come *before*
the subcommand — `adbctl.py --serial X mode doze-deep --on`, not `adbctl.py mode doze-deep --serial X`
(argparse rejects the latter with a bare usage dump that doesn't say why).

When a physical device and an emulator disagree, the useful question is **whether the mechanism is a
race or a hard block**:

- **Races diverge.** Data Saver and standby-restricted are won or lost on timing, so the faster
  device gets a different answer. Divergence here is a real finding.
- **Hard blocks don't.** Deep doze adds a firewall rule that is already in force when the flush
  runs, so there is no window to win and both devices behave identically. Identical results here are
  expected, not a sign the emulator is unrealistic.

`references/flush-matrix.md` records cases where they actually diverged and why.

## Guard rails that keep runs valid

These come from runs that produced clean-looking but meaningless data:

1. **A locked device invalidates everything.** The app launches behind the keyguard and backgrounds
   itself during any foreground wait, so the action lands on an already-backgrounded app.
   `adbctl.py` aborts when `deviceLocked=1`. If the device has a secure lock, ask the user to unlock
   it; do not attempt to bypass it.
2. **Screen-off scenarios run last**, since each one re-locks the phone. On a phone with a secure
   keyguard you get *one* screen-off run — spend it on the measurement, and run any screen-off
   control on the emulator instead.
3. **Check for premature `process ON_STOP`.** If it appears *before* the action marker, discard the
   run. `parse_logs.py` flags this automatically.
4. **Always restore device state**, even after a failure. A device left in airplane mode or battery
   saver silently corrupts the next run — and it is someone's actual phone. Clear
   `debug.bitdrift.internal_rust_log` too; it survives until reboot.
5. **Before a full sweep on a physical phone, ask the user to disable auto-lock.** Every screen-off
   scenario re-locks it, and each re-lock costs a manual unlock mid-run. `svc power stayon true`
   does *not* help — an explicit `KEYCODE_SLEEP` still sleeps and re-locks.
6. **One locked device must not abort the sweep.** `run_scenario.py` skips it per-device and
   continues; an early version called `sys.exit()` and silently stopped *every other* device.
7. **After fixing the parser, re-derive from the raw logcat.** Summaries written earlier
   (`state.json`, report tables) still encode the old bug. The capture is the only artifact that
   cannot be wrong.
8. **Verify a negative before believing it** — run `check_signatures.py`. "No upload happened" and
   "my pattern stopped matching" look identical in a summary.

## What the parser reports

`parse_logs.py` prints a timeline then a verdict, split so a foreground success can never be
mistaken for a backgrounding one:

```
FOREGROUND (before reference): 3 snapshot write(s) · 1 upload(s) enqueued, 2 acked
BACKGROUND (after reference):  snapshot written: YES · platform flush: 1 · forced: 1 · timed: 3
  upload ENQ/OK · ack 596ms
```

`upload` is `ENQ` / `DEBO` / `NONE`. **`DEBO` means the run measured the 30s minimum upload
interval, not the question** — idle longer in the foreground and repeat.

If the reference event never fired, both `parse_logs.py` and `run_scenario.py` refuse to print a
verdict and say so. That absence is usually itself the answer (see `recents` above) — treat it as a
result, not a harness failure.

A `DISK-FLUSH DEBOUNCE` line appears only on revs that **have** the disk-flush debounce window —
`c3ba1cba` and later, i.e. present on the `bump` branch and absent on `main`. Its absence is the rev,
not a regression; `check_signatures.py` reports the `stats-debounce` family as `UNSEEN` when you are
on a rev without it. When it does appear it validates the window by the invariant it implies
(consecutive writes never closer than the window). When `coalesced` is 0 the coalescing path is
untested and the output says so — don't read `HELD` as full coverage.

## Scenario files

`assets/scenarios/*.json` describe runs declaratively so they're repeatable and diffable:

```json
{
  "name": "minimal-background",
  "rust_log": "info,bd_client_stats=debug,bd_logger=debug,bd_api=debug",
  "modes_before_launch": [],
  "steps": [
    {"action": "launch"},
    {"action": "wait", "seconds": 35, "why": "clear the 30s minimum upload interval"},
    {"action": "home"},
    {"action": "wait", "seconds": 40}
  ],
  "reference_event": "process ON_STOP"
}
```

Bundled scenarios:

| File | Purpose |
|---|---|
| `minimal-background.json` | Baseline: launch → clear the interval → HOME → observe |
| `background-doze.json` | Same, with deep doze forced right after screen-off |
| `kill-after-background.json` | Background, then kill the process 5s later |
| `background-no-wait.json` | **Diagnostic**: deliberately skips the 35s wait. Use it to confirm the minimum-upload-interval gate is still active after a shared-core bump — it should report `DEBO`. |
| `matrix/T01…T14.json` | The full 14-scenario matrix (backgrounding method · restriction modes · exit variants). Run T04/T06/T07 **last** — they use screen-off and re-lock a physical phone. Results and rationale in `references/flush-matrix.md`. |

Add a `why` to any non-obvious wait. A future reader — including you — will otherwise assume it's
arbitrary and shorten it, which is how the 30s interval silently invalidated a whole matrix.

## Reference files

Read these as needed rather than upfront:

- **`references/device-modes.md`** — the nine states: setup, teardown, verification, when each must
  be applied, and the silent-failure traps. Read before using any mode.
- **`references/log-signatures.md`** — every log line worth matching, by subsystem, with what it
  proves and what it doesn't. Read before interpreting any capture.
- **`references/flush-matrix.md`** — a worked 14-scenario study of stats flushing on backgrounding,
  with results from an emulator and a Pixel 10. Read it as a template for structuring a
  multi-scenario investigation, or when the question is specifically about flush/upload behaviour.
