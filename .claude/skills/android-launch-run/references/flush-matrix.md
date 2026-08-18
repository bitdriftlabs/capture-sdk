# Stats flush & upload behaviour

The canonical reference for *when* the SDK flushes stats to disk, *when* it uploads, and what stops
either. Read this when the question is about flush/upload behaviour, or as a template for structuring
a multi-scenario investigation.

- [Reproducing it](#reproducing-it)
- [What triggers a flush](#what-triggers-a-flush)
- [The timers, and the two intervals people conflate](#the-timers-and-the-two-intervals-people-conflate)
- [The ON_STOP timeline](#the-on_stop-timeline)
- [What blocks an upload](#what-blocks-an-upload)
- [Current results](#current-results)
- [Behaviour that differs by rev](#behaviour-that-differs-by-rev)
- [Known gaps](#known-gaps)

> **Check the rev first:** `grep -m1 'bd-client-common' Cargo.toml`. Message wording and some
> behaviour track it. Everything below is measured on **`d8ac5975`** (the `bump` branch, PR #1107)
> unless a line says otherwise; see [Behaviour that differs by rev](#behaviour-that-differs-by-rev).
> The APK on the device is whatever was last built — reinstall after a branch switch *or* a rev bump.

## Reproducing it

```bash
S=.claude/skills/android-launch-run/scripts
python3 $S/selftest.py                                  # patterns still match? (no device needed)
python3 $S/adbctl.py install                             # only if the rev moved since the last build
python3 $S/sweep.py --out /tmp/sweep --serial <id>       # all 15 scenarios, screen-off last
python3 $S/check_signatures.py /tmp/sweep/T01-home/<id>/logcat.txt   # confirm nothing is UNSEEN
```

~15 min on one device. `sweep.py` prints one table; anything odd gets read from that scenario's
`summary.txt` and raw `logcat.txt`.

## What triggers a flush

Attribution is the most common source of wrong conclusions, because a flush looks identical in the
stats log regardless of what asked for it. Only two are on a clock:

| Trigger | Signature | Timing |
|---|---|---|
| **Periodic disk timer** | no `state flushing initiated` | anchored at the process's first flush, then every `stats.disk_flush_interval_ms` |
| **Periodic upload timer** | `prepared UPLOAD_REASON_PERIODIC …` | every `stats.upload_flush_interval_ms` |
| **Platform flush** (backgrounding) | **`state flushing initiated`** | `ON_STOP` + ~15ms |
| **API handshake** | `handshake stats upload completed: … source_files=N` | on stream establishment; can sweep a backlog |
| **Workflow `flush_buffers`** — *removed in `d8ac5975`* | `bd_workflows::engine: uploading due to flush buffers action` | **arbitrary**, server-configured |

The workflow one was the trap: no `state flushing initiated` (no platform bridge) and no timer
alignment, so it read as an unexplained flush whose timing depended on which workflow matched.
shared-core `184c3229` deletes it — the engine no longer calls `flush_trigger.flush()` on log-upload
approval — so on a current build the list is four. It is kept here because it is still live on
`c3ba1cba` and earlier, and because the diagnostic habit generalises: **an off-schedule flush with no
`state flushing initiated` comes from inside shared-core, and widening the filter beats guessing.**

```bash
# when a flush fits neither the timer nor the platform
adb -s <serial> shell setprop debug.bitdrift.internal_rust_log 'info,bd_client_stats=debug,bd_workflows=debug'
```

## The timers, and the two intervals people conflate

One log line names both, which is how they get mixed up:

```
stats disk flush interval 30s does not cleanly divide active upload interval 5s; falling back to upload cadence
```

- **`active upload interval`** is the upload *cadence* — how often an upload is attempted. Not a gate.
- **`stats.minimum_upload_interval_ms`** is the *floor*: it refuses any upload, periodic **or**
  `ON_STOP` flush, too soon after the last one.

Flags that decide timing, with defaults. Read what your account actually overrides from
`bd_runtime=debug` (`updated value of <flag> to <value>`); anything absent is at its default:

| Flag | Default | Effect |
|---|---|---|
| `stats.first_upload_flush_interval_ms` | 5s | first flush + upload of each **process** |
| `stats.disk_flush_interval_ms` | 60s | disk write cadence |
| `stats.upload_flush_interval_ms` | 60s | upload cadence |
| `stats.disk_flush_debounce_ms` | 1s | coalesce window after each write |
| `stats.minimum_upload_interval_ms` | 30s | anti-hammer floor on any upload |

**`effective_flush_interval()` requires the disk interval to evenly divide the upload interval.** At
startup the upload interval is 5s, so a 30s disk interval collapses to 5s and logs the `does not
cleanly divide` warning — a one-time startup artifact, not a misconfiguration. Once the upload
interval becomes 60s, `60 % 30 == 0` and it settles at 30s. A pairing like 30s/45s would silently
collapse to 45s.

Measured on a Pixel 10 with an account pushing `disk_flush_interval_ms` 60s→30s:

| Quantity | Value |
|---|---|
| first disk write + upload | t+5.66–5.71s |
| disk cadence | 30.00, 30.01, 30.01, 29.99 |
| upload cadence | 60.00, 60.00, 60.03 |
| debounce windows | 1.002–1.009s |

Two results that surprise people:

- **The 5s first-flush interval is per process start, not per install.** A force-stop and relaunch
  shows it again.
- **A forced flush does not reschedule the periodic timer.** After a backgrounding flush the next
  tick lands on the original anchor — observed as a forced write at t+152.64s then the scheduled one
  at t+155.67s (`5.66 + 5×30`). Two writes 3s apart, which the 1s window cannot merge. Not a bug.

### The floor must be armed to bite

`last_flush_upload_time` — the only thing `should_skip_upload()` reads — has exactly **one**
assignment site (`stats.rs:893`, the `EVENT_TRIGGERED` flush path), one clear-on-failure, and
initialises to `None`. So periodic and handshake uploads never arm it.

Until `184c3229` the workflow trigger fired a flush-path upload seconds after launch, arming the
floor in every process — which is why scenarios historically needed a ~35s foreground wait, and why
omitting it once invalidated a whole matrix. **With that trigger gone a fresh process reaches
`ON_STOP` unarmed**: measured, an `ON_STOP` upload allowed **1.88s** after a periodic upload. The
scenarios now wait 8s, only so one foreground flush lands as a positive control.

The floor is still real. Two backgroundings inside 30s trip it (`timing-double-background`): a first
`ON_STOP` upload arms it, and the second is refused 10.9s later with `skipping explicit stats upload`.

⚠ **If a flush-path upload ever reappears near startup, every scenario silently starts measuring the
floor instead of its subject.** `sweep.py` flags a `DEBO` row loudly for exactly this reason; if you
see one, the short waits are no longer safe.

## The ON_STOP timeline

`process ON_STOP` is the reference point for backgrounding work: the flush hangs off it, it lags the
activity's own `onStop` by the `ProcessLifecycleOwner` debounce, and it precedes `wm_on_stop_called`.

```
   -1.35s  >>> home
      +0ms  process ON_STOP                                    <- reference
      +4ms  bd_logger::logger: state flushing initiated           [platform asked]
     +25ms  bd_client_stats::file_manager: writing snapshot       <- disk write, durable
     +31ms  bd_client_stats::stats: prepared … stats upload       <- enqueued
    +974ms  bd_api::api: received ack for stats upload            <- server acked
    +4.99s  [netpolicy] background-allow revoked                  <- network cut
```

The flush wins that race by ~5×. Message wording differs on `main`; see
`log-signatures.md` for the per-rev table.

## What blocks an upload

**The disk write is never the problem.** It lands ~25–56ms after `ON_STOP` in every run, under every
restriction including airplane mode. Everything below is about the upload.

| Restriction | Effect | Shape |
|---|---|---|
| none | acks in 633–1535ms against a ~5s cutoff | — |
| battery saver | enqueued, **never acked** | hard block |
| doze deep / light | enqueued, **never acked** | hard block |
| Data Saver | device-dependent | **race** |
| standby-restricted | device-dependent | **race** |
| airplane | upload skipped, `stats are durable` | — |
| recents | **nothing runs at all** — no `ON_STOP` | — |

### Races diverge across devices; hard blocks do not

Use this before treating an emulator/phone difference as a finding:

- **Races diverge.** Data Saver and standby-restricted gate the socket around the time the upload
  flies, so the faster device wins. Divergence here is real.
- **Hard blocks don't.** Doze and battery saver are firewall rules already in force when the flush
  runs, so there is no window to win. Doze deep vs light agreed within 22ms on one device and within
  16ms across two. **Identical results on a hard block are expected, not evidence the emulator is
  unrealistic** — and the useful statement is *why* there was no difference.

Doze must be armed **before** `ON_STOP` to affect the flush at all; with the pre-arm order
`force-idle` lands ~1.1–1.2s before it. A restriction applied a minute after backgrounding tests
nothing, because network is already gone and the process frozen. See `device-modes.md`.

### Process death does not cost data

A kill *can* beat the upload — the old "it can't" result was an artifact of killing 4s after the ack:

| kill lands at | ack | outcome |
|---|---|---|
| `ON_STOP` + 390ms | none | **kill wins** |
| `ON_STOP` + 865ms | none | **kill wins** |
| `ON_STOP` + 1.32s | 974ms | ack wins |

Ack latency ranges 633–1535ms, so no fixed timing reliably lands on the ack's side; `T13-am-kill`
aims at ~+390ms, the side that reliably tests something.

**What a lost race costs is the upload attempt, not the data.** Traced by uuid across a process
boundary: snapshot `acc129cc…` written +25ms before the kill, then on the next launch
`prepared … metrics=43` → `received ack … error: ""` → `deleting pending upload: acc129cc…`.

## Current results

15 scenarios, Pixel 10 / API 37, `d8ac5975`, 0 errors. `BG disk` was **YES** in every run that
backgrounded.

| Scenario | upload | ack | netcut |
|---|---|---|---|
| `T01-home` | ENQ/OK | 1064–1439ms | 4.98s |
| `T02-back` | ENQ/OK | 662ms | **3.90s** |
| `T03-recents` | *no reference event* | — | — |
| `T04-screen-off` | ENQ/OK | 1535ms | 4.31s |
| `T05-battery-saver` | ENQ/**NONE** | — | 4.96s |
| `T06-doze-deep` | ENQ/**NONE** | — | 4.30s |
| `T07-doze-light` | ENQ/**NONE** | — | 4.28s |
| `T08-data-saver` | ENQ/OK | 976ms | 4.98s |
| `T09-standby-restricted` | ENQ/OK | 633ms | 4.99s |
| `T10-bg-restricted` | ENQ/OK | 1004ms | 4.98s |
| `T11-freezer` | ENQ/OK | 736ms | 4.93s |
| `T12-airplane` | NONE — skipped, durable | — | 4.96s |
| `T13-am-kill` | ENQ/**NONE** — kill won | — | 0.5s |
| `timing-cadence` | ENQ/OK | 878ms | 4.94s |
| `timing-double-background` | ENQ/OK, 2nd refused | 1322ms | 4.95s |

`T02-back` revokes network ~1.1s sooner than `home`, which shortens every race — worth knowing when
a result sits near the boundary.

## Behaviour that differs by rev

| | `bump` (`c3ba1cba`, `d8ac5975`+) | `main` (`42637e1f`) |
|---|---|---|
| stats message wording | new | legacy — see `log-signatures.md` |
| disk-flush debounce window | present | **absent** (`stats-debounce` reports `UNSEEN`) |
| airplane flush | writes to disk, skips upload | **wedges — no disk write at all** |
| workflow flush trigger | gone in `d8ac5975` | present |

**The airplane wedge is the sharpest difference.** On `main`, in airplane mode with an earlier upload
outstanding, the `ON_STOP` flush produced *no disk write* — `state flushing initiated` fired and the
flusher wedged behind the stalled upload. On `bump` the same scenario writes 4 snapshots and says so:

```
skipping explicit stats upload: another flush upload is active; stats are durable
```

*"stats are durable"* is the implementation stating the disk write is safe regardless.

## Known gaps

**The JVM-crash path (`flush(blocking=true)`, a hardcoded 500ms JNI wait)** is the only one that runs
a blocking flush, needs UI interaction, and has never been automated. Given acks of 633–1535ms it
would likely time out.

**The disk-flush coalescing branch is reachable only via `force_coalesce.py`**, never by a scenario —
it needs two flushes inside 1s, which declarative steps cannot schedule. The script pre-empts a
periodic tick with a platform flush and lands 3 coalesces per run (9 windows opened, 6 closed empty).
Worth remembering how this one went: the branch was documented as unexercised across 36 windows when
in fact the *detector* matched a message that does not exist. Any "never observed" claim about a
signature should be checked with `check_signatures.py` before it is written down — a detector that has
never fired is indistinguishable from a behaviour that never happens.
