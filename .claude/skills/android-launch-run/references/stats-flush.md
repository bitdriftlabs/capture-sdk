# Subject: stats flush & upload

When the SDK writes stats to disk, when it uploads them, and what stops either. Read
`lifecycle-investigation.md` for method and `log-signatures.md` for the exact lines.

Numbers here are **shapes, not constants** — re-measure them from your own capture. Message wording
and some behaviour track the pinned shared-core rev (`grep -m1 'bd-client-common' Cargo.toml`), so
confirm a signature against a real capture before reasoning from it.

- [Reproducing it](#reproducing-it)
- [What triggers a flush](#what-triggers-a-flush)
- [The two intervals people conflate](#the-two-intervals-people-conflate)
- [The timers](#the-timers)
- [The backgrounding timeline](#the-backgrounding-timeline)
- [What blocks an upload](#what-blocks-an-upload)
- [Process death does not cost data](#process-death-does-not-cost-data)
- [Known gaps](#known-gaps)

## Reproducing it

```bash
S=.claude/skills/android-launch-run/scripts
python3 $S/selftest.py                                   # patterns still match? (no device needed)
python3 $S/adbctl.py install                             # if the pinned rev moved since the last build
python3 $S/sweep.py --out /tmp/sweep --serial <id>       # every scenario, screen-off last
python3 $S/check_signatures.py /tmp/sweep/T01-home/<id>/logcat.txt   # confirm nothing is UNSEEN
```

`sweep.py` prints one table; anything odd gets read from that scenario's `summary.txt` and raw
`logcat.txt`. Regenerating the table is cheap, which is why no result snapshot is kept in this file —
a stale table is worse than none.

## What triggers a flush

Attribution is the most common source of wrong conclusions, because a flush looks identical in the
stats log regardless of what asked for it. Only the timers are on a clock:

| Trigger | Signature | Timing |
|---|---|---|
| **Periodic disk timer** | no `state flushing initiated` | anchored at the process's first flush, then every `stats.disk_flush_interval_ms` |
| **Periodic upload timer** | `prepared UPLOAD_REASON_PERIODIC …` | every `stats.upload_flush_interval_ms` |
| **Platform flush** (app-side call) | **`state flushing initiated`** | a few ms after whatever the app hangs it off |
| **API handshake** | `handshake stats upload completed: … source_files=N` | on stream establishment; can sweep a backlog |
| **Workflow `flush_buffers`** | `bd_workflows::engine: uploading due to flush buffers action` | **arbitrary**, server-configured |

The workflow trigger was the original trap: no platform marker and no timer alignment, so it read as
an unexplained flush whose timing depended on which workflow matched. It has since been removed from
shared-core, so on a current build the list is four — **but check rather than assume**, because it is
still live on older pinned revs and the diagnostic habit is what generalises: an off-schedule flush
with no `state flushing initiated` comes from inside shared-core, and widening the filter beats
guessing.

```bash
# when a flush fits neither the timer nor the platform
adb -s <serial> shell setprop debug.bitdrift.internal_rust_log 'info,bd_client_stats=debug,bd_workflows=debug'
```

## The two intervals people conflate

One log line names both, which is how they get mixed up:

```
stats disk flush interval 30s does not cleanly divide active upload interval 5s; falling back to upload cadence
```

- **`active upload interval`** is the upload *cadence* — how often an upload is attempted. Not a gate.
- **`stats.minimum_upload_interval_ms`** is the *floor*: it refuses any upload, periodic **or**
  flush-triggered, too soon after the last one.

Mistaking the cadence for the floor is how foreground waits get shortened and runs silently
invalidated.

### The floor must be armed to bite

`last_flush_upload_time` — the only thing the skip check reads — is assigned **only on the
flush-triggered upload path**, and initialises empty. Periodic and handshake uploads never arm it.

So a fresh process normally reaches its first backgrounding with the floor **unarmed**, and the
pre-backgrounding wait gates nothing on a single-backgrounding run. It is still worth keeping, but for
a different reason: letting the startup upload ack, so it isn't in flight competing with the upload
under test (see `lifecycle-investigation.md`).

Two backgroundings inside the floor still trip it — that is what `timing-double-background` exists to
show: the first arms the floor, the second is refused with `skipping … minimum upload interval has not
elapsed` while **the disk write still happens**. Durability does not depend on the upload.

⚠ **If any early-process trigger ever starts firing a flush-path upload again, every scenario silently
starts measuring the floor instead of its subject.** `sweep.py` flags a `DEBO` row loudly for exactly
this; if you see one, short waits are no longer safe.

## The timers

Flags that decide timing, with shared-core defaults. Read what your account actually overrides from
`bd_runtime=debug` (`updated value of <flag> to <value>`); anything absent is at its default:

| Flag | Default | Effect |
|---|---|---|
| `stats.first_upload_flush_interval_ms` | 5s | first flush + upload of each **process** |
| `stats.disk_flush_interval_ms` | 60s | disk write cadence |
| `stats.upload_flush_interval_ms` | 60s | upload cadence |
| `stats.disk_flush_debounce_ms` | 1s | coalesce window after each write |
| `stats.minimum_upload_interval_ms` | 30s | anti-hammer floor on any upload |

**The first-flush interval is per process start, not per install.** A force-stop and relaunch shows it
again.

**`effective_flush_interval()` requires the disk interval to evenly divide the upload interval.** At
startup the upload interval is the first-upload value, so a longer disk interval collapses to it and
logs the `does not cleanly divide` warning — a one-time startup artifact, not a misconfiguration. The
warning can also be computed from pre-push config, since runtime config arrives *after* the schedule
is first built; seen live, the warning quoted the default while the account's push landed a second
later. Check the pushed value before predicting any cadence: an interval that doesn't divide evenly
silently collapses to the upload cadence.

### An explicit flush re-anchors the periodic schedule

`reset_after_explicit_flush()` → `start_recurring_cycle()` re-anchors **both** deadlines at the flush,
so the next tick lands one interval after the flush rather than on the original anchor. Worth knowing
because the docs asserted the opposite for a while, and because it means a forced flush shifts every
subsequent prediction:

```
disk ticks … ─┤ FORCED ├─ next tick = forced + disk interval
                        └─ next periodic upload = forced + upload interval
```

The reset values follow the *recurring* intervals, never the first-upload one, because
`start_recurring_cycle()` clears the first-upload flag. `timing-reset-after-flush` measures this.

## The backgrounding timeline

For a platform flush hung off `process ON_STOP`, the shape is:

```
   −1.3s  >>> home
    +0ms  process ON_STOP                                    <- reference
    +4ms  bd_logger::logger: state flushing initiated           [platform asked]
   +25ms  bd_client_stats::file_manager: writing snapshot       <- disk write, durable
   +31ms  bd_client_stats::stats: prepared … stats upload       <- enqueued
     ~1s  bd_api::api: received ack for stats upload            <- server acked
     ~5s  [netpolicy] background-allow revoked                  <- network cut
```

The client contributes only tens of ms of that ack interval, so everything beyond it is transport and
backend. The flush normally wins the race against the network cutoff by several times over — but the
ack latency has a **heavy tail** (a tight sub-second mode with rare excursions an order of magnitude
out), so a single slow ack is not a regression. See `lifecycle-investigation.md` for why the median is
the only stable statistic here and why no fixed wait can cover the tail.

Under CPU throttling (battery saver, doze, screen off) the client-side offsets stretch by roughly an
order of magnitude while the ordering holds.

## What blocks an upload

**The disk write is never the problem.** It lands tens of ms after the reference event in every run,
under every restriction including airplane mode. Everything below is about the upload.

| Restriction | Effect | Shape |
|---|---|---|
| none | acks well inside the network-cutoff window | — |
| battery saver | enqueued, **never acked** | hard block |
| doze deep / light | enqueued, **never acked** | hard block |
| Data Saver | device-dependent | **race** |
| standby-restricted | device-dependent | **race** |
| airplane | upload skipped, `stats are durable` | — |
| app switcher (recents) | **nothing runs at all** — no `ON_STOP` | — |

`skipping explicit stats upload: another flush upload is active; stats are durable` is the
implementation stating the disk write is safe regardless of the upload. Treat it as a skip, not a
failure.

Restrictions must be armed **before** the reference event to affect the flush at all; see
`device-modes.md` for which ones must instead be applied after launch, and why.

## Process death does not cost data

A kill can beat the upload — whether it does is a race, decided by where the kill lands relative to
the ack:

| kill lands | outcome |
|---|---|
| a few hundred ms after the reference | **kill wins** — no ack |
| after the ack | ack wins |

Because ack latency spans a wide range, no fixed timing reliably lands on the ack's side.
`T13-am-kill` aims early deliberately: that is the side that reliably tests something.

**What a lost race costs is the upload attempt, not the data.** Traced by uuid across a process
boundary: the snapshot is written before the kill, and on the next launch the same uuid is prepared,
acked, then deleted from the pending set. The disk write is the durability guarantee; the upload is
best-effort.

## Known gaps

**The blocking-flush path** (`flush(blocking=true)`, a hardcoded JNI wait) is the only one that blocks,
is reached only from the JVM-crash handler, and has never been automated. Given typical ack latencies
it would likely time out — worth measuring rather than assuming.

**The disk-flush coalescing branch is reachable only via `force_coalesce.py`**, never by a scenario: it
needs two flushes inside a sub-second window, which declarative steps cannot schedule. Worth
remembering how that one went — the branch was written up as "unexercised across 36 windows" when in
fact the *detector* matched a message the SDK never emitted. Any "never observed" claim about a
signature should be checked with `check_signatures.py` before it is written down.
