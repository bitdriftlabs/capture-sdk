---
name: android-launch-run
description: >-
  Build, install, launch, background, and kill the capture-sdk Android test apps on every connected
  device or emulator, drive them through app-lifecycle and UI transitions, put those devices into
  specific power/network states, and capture and parse logcat to answer questions about SDK runtime
  behaviour. Use this whenever the user wants to run an Android app on a device or emulator,
  reproduce behaviour under Android restrictions (doze, battery saver, Data Saver, standby buckets,
  cached-app freezer, airplane mode, background-restricted), verify what the SDK does on
  backgrounding, focus loss, configuration change or process death, or read bitdrift Rust logs
  (bd_client_stats, bd_logger, bd_api, bd_buffer) out of logcat. Reach for it for anything involving
  adb device state, RUST_LOG levels via debug.bitdrift.internal_rust_log, lifecycle timing, or
  flush/upload verification — including questions phrased as "does X still happen when the app is
  backgrounded?" or "why didn't the upload go out?", even when adb and logcat are never mentioned.
---

# Android launch & run

Observe what the SDK actually does at runtime instead of guessing. The core loop:

**pick devices → set log levels → apply device state → launch → act → capture → verify patterns → parse**

This is a general harness for **app-lifecycle and device-state experiments**. A run is always the
same shape — drive the app through a transition, capture both logcat buffers on the device clock,
then decide what the capture proves. Stats flushing was the first subject put through it, and the
`references/` subject files are examples of the pattern rather than the point of the skill. Adding a
new subject is a scenario file plus, at most, a pattern (see
[Adding a new subject](#adding-a-new-subject)).

Three rules carry most of the value.

**Timing is usually the answer.** Most interesting questions ("did the work finish before the OS cut
the network?") are races, so every timestamp must come from the **device clock**. Never compare a
host-side `date` against a logcat timestamp; they drift by seconds.

**Runtime and source answer different halves of the question.** The capture tells you *what*
happened and *when*. The Kotlin tells you *why* and *where a fix would go*. Neither substitutes for
the other, and reading the relevant handler is cheap — usually one file. "The app switcher doesn't
flush" is a fact; adding that the flush hangs off `ProcessLifecycleOwner`'s `ON_STOP`, which the app
switcher never fires, turns it into a diagnosis with an obvious fix site. Do the run, then spend two
minutes in the handler before you write up a mechanism.

**Never trust a negative you have not verified.** "It didn't happen" and "my pattern stopped
matching" are indistinguishable in a summary, and both a false regression report and a false pass
have already come from exactly that. See [Before you trust a negative](#before-you-trust-a-negative).

## Quick start

```bash
S=.claude/skills/android-launch-run/scripts     # run from the repo root
A=.claude/skills/android-launch-run/assets

python3 $S/adbctl.py devices                    # what's connected, and is anything locked
python3 $S/adbctl.py install                    # build + install debug app everywhere
python3 $S/run_scenario.py $A/scenarios/matrix/T01-home.json --out /tmp/run1
```

`run_scenario.py` is the main entry point — it applies state, launches, acts, captures, and parses,
per device, and writes `<out>/<serial>/{logcat.txt, summary.txt, state.json}`. `adbctl.py` is for
driving one step at a time when no scenario fits.

Re-parse or re-check a capture you already have:

```bash
python3 $S/parse_logs.py       /tmp/run1/<serial>/logcat.txt --ref "process ON_STOP"
python3 $S/check_signatures.py /tmp/run1/<serial>/logcat.txt
```

Run every bundled scenario and get one table instead of a pile of summaries:

```bash
python3 $S/sweep.py --out /tmp/sweep --serial <id>        # add --only T01,T05 to subset
```

## Before you trust a negative

`check_signatures.py` settles "dead detector or absent behaviour?" mechanically. It asks *can I still
see this class of event at all?*, prints `SEEN` / `UNSEEN` per family, and lists every `bd_*` line the
parser did **not** recognise, normalised and grouped:

```
  SEEN   stats-flush          FORCED=2 TICK=5 MERGE=5 SNAP=5 FLUSHED=2
  SEEN   stats-upload         PREP=0 ENQ=2 DISPATCH=0 ACK=3 RES=3
  UNSEEN stats-debounce       DEBOUNCE_OPEN=0 DEBOUNCE_SHUT=0 DEBOUNCE_COALESCE=0
```

An `UNSEEN` family means a zero elsewhere proves nothing — the detector itself may be dead. A renamed
message shows up in the unrecognised list immediately.

`selftest.py` comes at the same problem from the other side: `check_signatures.py` asks whether *this
capture* still contains each family, `selftest.py` asks whether each pattern still matches a
known-good line at all. It needs no device and exits non-zero, so it can fail a build:

```bash
python3 $S/selftest.py                        # every pattern vs its stored real sample
python3 $S/selftest.py --capture <logcat.txt> # plus an end-to-end parse
```

It exists because the parser has been wrong several times, each found by accident *after* it had
produced a wrong answer. It also fails when a *new* pattern is added without a sample, since an
unsampled pattern is untested by construction — which is how a pattern that matched a message the SDK
never emitted survived long enough to be written up as a finding.

**Rust log wording tracks the pinned shared-core rev**, not a local `../shared-core` checkout:

```bash
grep -m1 'bd-client-common' Cargo.toml     # the rev every log wording follows
```

Run `selftest.py` after any shared-core bump and `check_signatures.py` once per investigation. When
the pinned rev moves, **reinstall** — the APK is whatever was last built, and a rev bump is exactly
when it goes stale. `run_scenario.py` warns when the installed APK predates the pinned config.

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
`-Prust-target=x86_64` for an x86 emulator. The first build is slow (Bazel builds the Rust lib);
subsequent ones are minutes.

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

Two ways this silently doesn't take effect, both of which look like "the SDK ignores my log level":

1. **`adb shell` without `-s` aborts when more than one device is attached** — it prints
   `adb: more than one device/emulator` and does nothing. Among build output that line is easy to
   miss, and the property just stays empty. Prefer `adbctl.py logprop '<filter>'`, which fans out
   across every connected device and reports how many it set.
2. **The property is read once at native init**, so it cannot affect an already-running process.
   Force-stop and relaunch. `run_scenario.py` does this for you.

If the app really is ignoring a correctly-set property, check it is debuggable
(`dumpsys package <pkg> | grep flags=` should list `DEBUGGABLE`) — `installDebug` produces one, so a
missing flag means something other than this skill installed the APK.

Pick the narrowest filter that answers the question — `bd=trace` is enormous and will flood the
buffer, evicting the very lines you came for.

| Question | Filter |
|---|---|
| Stats flush + disk write | `info,bd_client_stats=debug` |
| **Did an upload actually land?** | `info,bd_client_stats=debug,bd_api=debug` |
| …plus *who* triggered the flush | add `,bd_logger=debug` |
| Log (not stats) uploads | `info,bd_logger=debug` |
| Ring buffer to disk | `info,bd_buffer::ring_buffer=debug` |
| **An unexplained flush** | add `,bd_workflows=debug` — server workflows can raise their own flushes |
| Which runtime flags the account overrides | add `,bd_runtime=debug` |

**Any delivery question needs `bd_api=debug`.** The server ack is logged at the transport layer, not
by the subsystem that enqueued the upload — so a subsystem-only filter hides the most direct evidence
that an upload landed and leaves you inferring delivery from the enqueue.

**When you do need `bd=trace`, raise the logcat buffer first** or the volume evicts what you came for:

```bash
adb -s <serial> shell logcat -g -b events   # read the device default BEFORE changing anything
adb -s <serial> shell logcat -G 128M        # -G hits several buffers at once
# … capture …
adb -s <serial> shell logcat -b main -G 256K   # and restore each one you changed
```

Record the original rather than guessing at it. A buffer you did *not* touch is the best witness to
the default, since `-G` without `-b` does not necessarily cover every buffer.

## Reading the logs

Four independent sources, all on the device clock, so they interleave correctly in one capture:

| Source | Buffer | Gives |
|---|---|---|
| `bitdrift-lifecycle` (INFO) | main | **process** lifecycle + window focus, from the test app |
| `wm_*`, `input_focus`, `am_freeze` | events | OS-side activity lifecycle, focus, freeze |
| `bd_*::*` (Rust) | main | SDK internals; tag is the full Rust module path |
| `dumpsys netpolicy` | — | when the OS revoked network (post-run query) |

Capture both buffers together — `-b main,events` — or the OS events and app logs can't be correlated.

### Choosing the reference event

Every scenario declares a `reference_event`: the line that splits "before" from "after" so foreground
work can never be mistaken for the behaviour under test. Choosing it wrongly is the most common way a
run measures nothing.

- **`process ON_STOP`** — the app truly backgrounded. Correct for backgrounding work, wrong for
  anything the app does while still visible, because it never fires there.
- **`window focus=false`** — the app is losing foreground *attention*. Fires strictly earlier than
  `ON_STOP`, and fires in cases where `ON_STOP` never does (app switcher, dialogs, activity
  transitions), which makes it the only observable signal for those.
- **A marker you stamp yourself** (`adbctl.py mark`) — for anything with no natural log line.

If the reference never fires, `parse_logs.py` and `run_scenario.py` refuse to print a verdict and say
so. **That absence is frequently the result, not a harness failure** — a scenario whose reference is
`ON_STOP` reporting "no reference event" for the app switcher *is* the finding.

For what each line means — and which are safe to draw conclusions from — read
**`references/log-signatures.md`**. One discriminator worth knowing upfront:
**`state flushing initiated`** marks a *platform-triggered* flush. The underlying call is reachable
only from a platform bridge, so its presence means Kotlin (or Swift) asked; its absence means
something inside shared-core did.

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

- **A restriction applied *after* the work already ran tests nothing.** Backgrounding alone kills
  network within seconds and freezes the process within a minute or so. Force doze a minute later and
  you will correctly observe "no uploads" — while having isolated nothing, because everything was
  already dead. A restriction must be armed *before* the reference event to affect the work.
- **`standby-restricted` is silently reset by launching the app**, so it must be applied after.
- **`data-saver` does nothing** unless the network is first marked metered.
- **`doze` can only be forced after the screen is off** and the device is unplugged.

Use `modes_before_launch` for anything that must be in force when the work runs, and a `mode` step
for anything that must land after. **Read `references/device-modes.md` before using any of them** —
it documents which is which, with the evidence.

Always pair a restriction with an unrestricted control run. "No upload under doze" only means
something next to "upload acked in well under a second without it".

## Actions

```bash
python3 $S/adbctl.py action launch|home|back|recents|screen-off|wake|kill|force-stop|freeze|unfreeze
python3 $S/adbctl.py action rotate|rotate-reset|launch-activity|tap-text|revoke-permission
python3 $S/adbctl.py action wait --seconds 35
```

`home`, `back`, `recents` and `screen-off` are all different, and not interchangeable:

- **`recents`** does not fire `ON_STOP` at all — the activity stays "started" while the overview is
  open (the carousel renders a live tile, so the OS still reports it visible), so no backgrounding
  work runs. If a scenario appears to do nothing, check for this first. Moving on to another app
  *does* fire `ON_STOP`, so it defers backgrounding work rather than losing it.
- **`back`** revokes network sooner than `home`, so it shortens any race.
- **`screen-off`** re-locks a phone with a secure keyguard, which invalidates every later run.

### Driving the UI

Two actions reach transitions the keyevents can't, which is what turns a manual row into a scenario:

- **`tap-text`** (step key `text`) — `uiautomator dump`, find the first node whose text or
  resource-id matches the case-insensitive regex, tap its center. Taps survive layout and DPI changes
  that hardcoded coordinates don't. Match loosely: dialog buttons often render a **curly apostrophe**,
  so `Don.t allow` matches where `Don't allow` does not.
- **`revoke-permission`** (step key `permission`) — an already-granted permission returns instantly
  with no dialog, so revoke before driving a permission-dialog run. **`pm revoke` alone is not
  enough**: two denials set `USER_FIXED` ("don't ask again"), after which the OS answers immediately
  and never renders a dialog again — a scenario that only revokes therefore works once and then
  silently measures nothing. The action clears `user-fixed`/`user-set` first; check with
  `dumpsys package <pkg> | grep <PERM>`. For the same reason, dismiss a permission dialog with
  **BACK** (a cancellation) rather than tapping *Don't allow* (a recorded decision).

**When a UI-driven run produces a negative, prove the UI state actually changed before believing it.**
A tap that silently missed and a transition that genuinely doesn't fire look identical in the
capture. `dumpsys input_method | grep mInputShown` confirms a keyboard is really up; `uiautomator
dump` confirms a dialog is really showing. That check is the difference between a platform finding
and a broken tap.

## Marking actions in the log

Actions are stamped into logcat on the device clock so they appear inline in the timeline:

```bash
adb -s <serial> shell log -p i -t ANDROID-RUN "ACTION home"
```

`adbctl.py` does this automatically. Do it manually if you're driving adb yourself — otherwise you'll
be reduced to guessing when the action landed relative to what the app did.

## Multi-device

`adbctl.py` and `run_scenario.py` target **every connected device** by default, in parallel, and write
per-device output directories. Use `--serial <id>` for one device, or `--sequential` when the numbers
matter: parallel runs add tens of ms of adb and logcat jitter, which is fine for pass/fail but can
distort sub-second measurements.

On `adbctl.py`, `--serial`, `--app` and `--sequential` are **global** flags and must come *before* the
subcommand — `adbctl.py --serial X mode doze-deep --on`, not `adbctl.py mode doze-deep --serial X`
(argparse rejects the latter with a bare usage dump that doesn't say why).

When a physical device and an emulator disagree, ask **whether the mechanism is a race or a hard
block** before calling it a finding:

- **Races diverge.** Data Saver and standby-restricted are won or lost on timing, so the faster device
  gets a different answer. Divergence here is real.
- **Hard blocks don't.** Deep doze adds a firewall rule already in force when the work runs, so there
  is no window to win and both devices behave identically. Identical results here are expected, not a
  sign the emulator is unrealistic.

## Guard rails that keep runs valid

These come from runs that produced clean-looking but meaningless data:

1. **A locked device invalidates everything.** The app launches behind the keyguard and backgrounds
   itself during any foreground wait, so the action lands on an already-backgrounded app.
   `adbctl.py` aborts when `deviceLocked=1`. If the device has a secure lock, ask the user to unlock
   it; do not attempt to bypass it.
2. **Screen-off scenarios run last**, since each one re-locks the phone. On a phone with a secure
   keyguard you get *one* screen-off run — spend it on the measurement, and run any screen-off
   control on an emulator instead.
3. **Check the reference event did not fire early.** If it appears *before* the action marker, discard
   the run; the app was already in the state you meant to drive it into. `parse_logs.py` flags this.
4. **Always restore device state**, even after a failure. A device left in airplane mode or battery
   saver silently corrupts the next run — and it is someone's actual phone. Clear
   `debug.bitdrift.internal_rust_log` too; it survives until reboot. Anything a run changes that
   outlives it (permission flags, rotation, buffer sizes) has to be restored the same way.
5. **Before a full sweep on a physical phone, ask the user to disable auto-lock.** Every screen-off
   scenario re-locks it, and each re-lock costs a manual unlock mid-run. `svc power stayon true`
   does *not* help — an explicit `KEYCODE_SLEEP` still sleeps and re-locks.
6. **One locked device must not abort the sweep.** `run_scenario.py` skips it per-device and
   continues; an early version called `sys.exit()` and silently stopped *every other* device.
7. **After fixing the parser, re-derive from the raw logcat.** Summaries written earlier
   (`state.json`, report tables) still encode the old bug. The capture is the only artifact that
   cannot be wrong.
8. **Verify a negative before believing it** — run `check_signatures.py`. This applies with double
   force to a count that has *always* been zero: a detector that has never fired is not evidence of
   anything.
9. **Reinstall after switching branches or bumping a rev.** The APK is whatever was last built;
   changing `Cargo.toml` does not change the device.
10. **Attribute an event before explaining it.** Several things can trigger the same-looking work and
    only some are on a clock. An off-schedule event with no platform marker comes from inside
    shared-core — widen the filter (`bd_workflows=debug`) rather than inventing a client-side theory.
11. **A scenario must be repeatable.** If a run leaves state that changes the next run's outcome, it
    is a one-shot measurement pretending to be a scenario. Reset that state in a first step, and
    prove it by running the scenario twice in a row.

## What the parser reports

`parse_logs.py` prints a timeline then a verdict, split so foreground success can never be mistaken
for the behaviour under test:

```
FOREGROUND (before reference): 3 snapshot write(s) · 1 upload(s) enqueued, 2 acked
BACKGROUND (after reference):  snapshot written: YES · platform flush: 1 · forced: 1 · timed: 3
  upload ENQ/OK · ack 596ms
```

`upload` is `ENQ` / `DEBO` / `NONE`. **`DEBO` means the run measured the minimum-upload-interval floor
rather than the question** — idle longer in the foreground and repeat.

A `DISK-FLUSH DEBOUNCE` line appears only when the pinned shared-core rev has the disk-flush debounce
window. Its absence is the rev, not a regression; `check_signatures.py` reports the `stats-debounce`
family as `UNSEEN` in that case. When it does appear it validates the window by the invariant it
implies (consecutive writes never closer than the window). When `coalesced` is 0 the coalescing path
is untested and the output says so — don't read `HELD` as full coverage.

## Scenario files

`assets/scenarios/*.json` describe runs declaratively so they're repeatable and diffable:

```json
{
  "name": "T01-home",
  "rust_log": "info,bd_client_stats=debug,bd_logger=debug,bd_api=debug",
  "modes_before_launch": [],
  "steps": [
    {"action": "launch"},
    {"action": "wait", "seconds": 18, "why": "let the startup upload ack before backgrounding"},
    {"action": "home"},
    {"action": "wait", "seconds": 40}
  ],
  "reference_event": "process ON_STOP"
}
```

Step keys: `action`, plus `seconds` (wait), `name` (mode), `component` (launch-activity), `text`
(tap-text), `permission` (revoke-permission), and `why` on anything non-obvious.

Bundled scenarios, grouped by what they vary:

| Group | Varies | Reference |
|---|---|---|
| `matrix/T01…T04` | **backgrounding method**: home · back · recents · screen-off. `T01-home` is the baseline every other scenario varies from | `ON_STOP` |
| `matrix/T05…T12` | **restriction mode**, backgrounding held constant: battery-saver · doze deep/light · data-saver · standby-restricted · bg-restricted · airplane | `ON_STOP` |
| `matrix/T11`, `matrix/T13` | **process exit**: cached-app freeze, and a kill timed to land inside the upload ack window | `ON_STOP` |
| `timing-*` | **the SDK's own timers**: first-flush and recurring cadences, whether an explicit flush re-anchors the schedule, and the upload floor | `ON_STOP` |
| `focus-*` | **foreground-attention loss** where the app often never backgrounds: recents · recents+kill · home · activity transition · rotation · permission dialog | `window focus=false` |

Run the screen-off scenarios last (`T04`, `T06`, `T07`). On a device whose
`lock_screen_lock_after_timeout` is short this re-locks the phone and invalidates everything after;
check that setting rather than assuming either way.

Add a `why` to any non-obvious wait. A future reader — including you — will otherwise assume it's
arbitrary and shorten it, which is how a whole matrix once got silently invalidated by an upload floor
nobody had cleared.

### Adding a new subject

The harness is generic; a new subject usually needs no Python at all.

1. **Pick the reference event** the behaviour hangs off (see
   [Choosing the reference event](#choosing-the-reference-event)). If the app doesn't already log it,
   stamp a marker.
2. **Write the scenario**: a `launch`, a settle `wait` with a `why`, the transition, then a `wait` long
   enough for the consequence. Add a **reset step first** if the transition depends on state a
   previous run consumed (guard rail 11).
3. **Run it, then read the raw capture** before trusting any summary. Confirm the transition itself
   shows up; a missing transition is a harness problem, a present transition with no consequence is a
   finding.
4. **Only add a pattern to `parse_logs.py`** if the verdict needs a new event class — and add a real
   sample to `selftest.py` in the same change, or the pattern is untested by construction.
5. **Write the subject file** in `references/` when the findings outgrow the scenario's `description`:
   what the mechanism is, which signals prove it, and what invalidates a run. Keep measurements as
   illustrative shapes, not pinned facts.
6. **Record platform surprises with the check that proved them.** "The IME does not drop window focus
   on this hardware" is only useful next to `dumpsys input_method | grep mInputShown`, which is what
   makes it a finding instead of a suspected broken tap.

## Scripts

| Script | Use |
|---|---|
| `adbctl.py` | one step at a time: devices, install, logprop, mode, action, mark, state |
| `run_scenario.py` | run a declarative scenario end to end, per device, and parse it |
| `sweep.py` | run **every** scenario in order (screen-off last) and print one comparable table |
| `parse_logs.py` | timeline + verdict from a capture |
| `check_signatures.py` | **run before trusting any negative**: SEEN/UNSEEN per family, unrecognised lines |
| `selftest.py` | **run after any shared-core bump**: asserts every pattern still matches a real line |
| `force_coalesce.py` | deliberately land two stats flushes inside the disk-flush debounce window |

`sweep.py` orders screen-off scenarios last and resets device state between runs even when one raises,
so a single bad scenario cannot cost the rest. It reports `no reference event` rather than a verdict
where the reference never fired — which for some scenarios is the expected result.

`force_coalesce.py` exists because the coalescing branch needs two flushes inside a sub-second window,
which declarative steps cannot schedule. It pre-empts rather than reacts: the periodic tick is
predictable (anchor + period·k) while a platform flush arrives a fixed delay after HOME, so it observes
one tick and schedules HOME to land the forced flush inside the *next* window. Reacting to a tick is
always too late — the reaction costs more than the window is wide. Its `--home-to-flush` default is
calibrated on one phone including logcat streaming latency; if every attempt lands just outside the
window, re-measure that value from a real run rather than nudging `--target-offset` blindly.

## Reference files

Read these as needed rather than upfront:

- **`references/device-modes.md`** — the nine device states: setup, teardown, verification, when each
  must be applied, and the silent-failure traps. Read before using any mode.
- **`references/log-signatures.md`** — every log line worth matching, by subsystem, with what it proves
  and what it doesn't. Read before interpreting any capture.
- **`references/lifecycle-investigation.md`** — how to design a run that measures what you asked:
  attribution, races vs hard blocks, what invalidates a result, and how to read a missing signal. Read
  for any new investigation.
- **`references/stats-flush.md`** — subject reference: when the SDK flushes stats to disk, when it
  uploads, and what stops either.
- **`references/window-focus-flush.md`** — subject reference: flushing on window-focus loss, including
  the transitions that do *not* drop focus.
