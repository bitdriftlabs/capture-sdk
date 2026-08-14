# Log signatures

Every line worth matching, what it proves, and what it does **not**. All timestamps are on the
device clock, so sources interleave correctly in a single `-b main,events` capture.

- [1. App-side lifecycle](#1-app-side-lifecycle-bitdrift-lifecycle)
- [2. OS-side lifecycle](#2-os-side-lifecycle-events-buffer)
- [3. Flush attribution](#3-flush-attribution-bd_logger)
- [4. Stats](#4-stats-bd_client_stats)
- [5. Transport](#5-transport-bd_api)
- [6. Ring buffers and log uploads](#6-ring-buffers-and-log-uploads)
- [7. Network cutoff](#7-network-cutoff-dumpsys-netpolicy)
- [8. Traps](#8-traps)

> ⚠ **Log text tracks the pinned shared-core rev**, from `capture-sdk/Cargo.toml` — not a local
> `../shared-core` checkout, which is often at a different commit with different wording. One real
> example: the local tree read `stat upload attempt complete` while the shipped binary logged
> `stat flush upload attempt complete`. Confirm any signature against a real capture before
> reasoning from it.

---

## 1. App-side lifecycle (`bitdrift-lifecycle`)

INFO, from `gradle-test-app`'s `LifecycleEventLogger`. Present regardless of Rust log level.

| Line | Meaning |
|---|---|
| `process ON_CREATE\|ON_START\|ON_RESUME\|ON_PAUSE\|ON_STOP` | `ProcessLifecycleOwner` events |
| `window <Activity> focus=true\|false` | `Activity.onWindowFocusChanged` |

**`process ON_STOP` is the canonical reference point** for backgrounding work. It is what the SDK's
flush hangs off, and `ProcessLifecycleOwner` debounces stop events by ~700ms, so it deliberately
lags the activity's own `onStop`.

`window … focus=false` is the *earliest* signal the app is leaving the foreground — roughly
**1050–1090ms** before `process ON_STOP`, measured repeatedly on both an emulator and a Pixel 10.
It also fires in cases where `ON_STOP` never does (see `recents` below), which makes it the only
way to observe those.

## 2. OS-side lifecycle (`events` buffer)

No app cooperation needed, so this works against any app, including release builds.

| Tag | Meaning |
|---|---|
| `wm_on_paused_called`, `wm_pause_activity` | Activity paused |
| `wm_on_stop_called`, `wm_stop_activity` | Activity stopped — **not** the process-level event |
| `wm_on_top_resumed_gained_called` / `…lost_called` | Top-resumed transitions |
| `input_focus` | `Focus leaving …` / `ViewRootImpl focus=false` |
| `am_freeze` | Cached-app freezer acting on the process |
| `wm_task_moved`, `wm_task_to_front` | Task movement |

Tag names vary by Android version — older releases use `am_on_stop_called` rather than
`wm_on_stop_called`. Match both. Verified present on API 36 and 37.

`wm_on_stop_called` fires **~28ms after** `process ON_STOP`, so it is not a substitute for the
app-side signal when timing matters.

## 3. Flush attribution (`bd_logger`)

Needs `bd_logger=debug`.

| Line | Tag | Proves |
|---|---|---|
| `state flushing initiated` | `bd_logger::logger` | **A platform bridge asked for a flush** |
| `flush state: completion received` | `bd_logger::async_log_buffer` | A *blocking* flush completed |
| `flush state: received an error when waiting for completion: …` | `bd_logger::async_log_buffer` | A blocking flush **timed out** — the JNI wait is hardcoded to 500ms |

`state flushing initiated` is the durable platform-vs-internal discriminator: `Logger::flush_state`
is reachable only from the JNI/Swift bridges, never from inside shared-core. So it present ⇒ Kotlin
(or Swift) asked; absent ⇒ an internal timer or workflow did.

This matters because **"forced" does not mean "platform-triggered."** A foreground-only run with no
`flush()` call anywhere still produced five forced stats flushes — shared-core's workflow path
raises its own flush requests. Attribute with `state flushing initiated`, not with the stats log.

The blocking-flush lines only exist for `flush(blocking=true)`, which on Android is reached only
from `AppExitLogger.onJvmCrash` — and is skipped entirely when fatal-issue reporting is
initialized.

## 4. Stats (`bd_client_stats`)

Needs `bd_client_stats=debug`. **Match the tag prefix**, not an enumerated list — the subsystem
spans several modules (`::stats`, `::file_manager`, …) and new lines get added.

```
grep -E "bd_client_stats[^:]*::" capture.txt
```

The lines that carry meaning:

| Line | Tag | Meaning |
|---|---|---|
| `received a signal to flush stats to disk` | `::stats` | A **forced** flush request was dequeued |
| `processing flush to disk tick` | `::stats` | `flush_to_disk()` entered — fires on **both** forced and timed paths |
| `updating aggregated snapshot file with N metrics` | `::stats` | Merge, with the metric count |
| **`writing snapshot: stats_uploads/<uuid>`** | `::file_manager` | **The actual disk write.** Fires on both paths |
| `stats flushed` | `::stats` | A forced flush ran to completion — **forced path only** |
| `processing upload from disk` | `::stats` | Upload staging |
| `sending pending flush upload: <uuid> with N metrics` | `::stats` | Upload dispatched |
| `skipping flush upload, minimum interval not elapsed` | `::stats` | Debounced by `stats.minimum_upload_interval_ms` (30s) |
| `flush already in progress, skipping` | `::stats` | Request **dropped entirely** — no disk write, no upload |
| `stat flush upload attempt complete: UploadResponse { success: <bool>, uuid: "<uuid>" }` | `::stats` | Upload result |

### Which line proves a disk write

Use **`writing snapshot`**. It is the file write and it fires on both the forced and timed paths.

`stats flushed` is *not* the disk-write proof: it only ever appears on the forced path, so a timed
flush writes to disk while logging nothing of the sort. Reasoning from its absence will produce a
false "no disk write" conclusion. Observed order on the forced path:

```
received a signal → processing flush to disk tick → updating aggregated snapshot
  → writing snapshot   ← the write
  → stats flushed      ← completion marker, ~9ms later
  → sending pending flush upload
```

### Classifying a flush

| Kind | Signature |
|---|---|
| Platform-triggered | `state flushing initiated`, then `received a signal…` within ~300ms |
| Internal forced | `received a signal…` with **no** preceding `state flushing initiated` |
| Timed | `processing flush to disk tick` with **no** `received a signal…` just before |
| Dropped | `flush already in progress, skipping` |

Counting `processing flush to disk tick` alone over-counts timed flushes, because forced flushes
emit it too.

### Correlating uploads

Match `sending pending flush upload` to `stat flush upload attempt complete` **by uuid, never by
order**. Acks arrive out of order, can take 475ms–17s, and may reference an upload from a
*previous process run* recovered from disk at startup.

## 5. Transport (`bd_api`)

Needs `bd_api=debug`. Explains *why* an upload didn't land.

| Line | Meaning |
|---|---|
| `starting new stream` / `sending handshake` / `received handshake` | Connection established |
| `stream closed due to '<reason>'` | Teardown. `SocketException: Software caused connection abort` = the OS cut it; `UnknownHostException` = DNS blocked |
| `reconnecting in N ms` | Backoff — escalates fast (263ms → 2.5s → 17s → 91s) |
| `received ack for stats upload "<uuid>", error: ""` | Server ack |

A backgrounded app blocked by the firewall shows `UnknownHostException` rather than a connect
error, because the block covers DNS too.

## 6. Ring buffers and log uploads

Separate from stats, and **not** touched by `flush()`, which only makes buffers durable on disk.

| Line | Tag | Meaning |
|---|---|---|
| `buffer_id=<id> signaled to flush` | `bd_buffer::ring_buffer` | Buffer told to drain to disk |
| `buffer_id=<id> signaled to flush not found` | `bd_buffer::ring_buffer` | ⚠ Buffer missing — this path `return`s and can stop later flushes |
| `flushing logs due to deadline hit` | `bd_logger::consumer` | `log_uploader.batch_deadline_ms` (30s) fired |
| `flushing N logs` / `flushing N logs from trigger artifact` | `bd_logger::consumer` | Batch dispatched |
| `completed continuous upload with result: …` | `bd_logger::consumer` | Log upload result |
| `triggered flush buffer action IDs: {…}` | `bd_logger::log_replay` | Workflow-driven buffer flush |

`flush_state` runs four things concurrently (`tokio::join!`): stats flush **with upload**, all ring
buffers to disk, session persist, workflow persist. It does **not** dispatch a log upload — those
run on the consumer's own batch deadline. So a missing log upload after a flush is expected, not a
failure.

## 7. Network cutoff (`dumpsys netpolicy`)

Queried after the run, not from logcat. Timestamps are device-clock, so they compare directly.

```bash
adb -s <serial> shell dumpsys netpolicy | grep '<uid>-background'   # event log: when allow was revoked
adb -s <serial> shell dumpsys netpolicy | grep 'UID=<uid> state='   # authoritative current state
```

Grepping the dump returns **event-log** lines (`… - Firewall rule changed: <uid>-background-default`),
which is what you want for timing. The *current* state is the `effective=` field of
`UID=<uid> state={…}` — do not poll the event log for current state.

`effective=NONE` means unblocked. `effective=APP_BACKGROUND` means the background firewall chain
has revoked it. Other reasons OR in: `DOZE`, `DATA_SAVER`, `METERED_USER_RESTRICTED`,
`POWER_SAVE_MODE`.

## 8. Traps

1. **`stats flushed` is not a disk-write signal** — forced path only. Use `writing snapshot`.
2. **`processing flush to disk tick` fires on forced flushes too** — don't count it as "timed".
3. **"Forced" ≠ platform-triggered** — use `state flushing initiated`.
4. **Correlate uploads by uuid**, not order; acks can be seconds late or from a prior process.
5. **The 30s upload debounce hides everything else.** Background sooner than 30s after the last
   flush-triggered upload and the upload is suppressed before network or device state matters. A
   run showing `skipping flush upload, minimum interval not elapsed` measured only the debounce.
6. **Rust logcat tags are full module paths** (`bd_client_stats::file_manager`). Grepping
   `bd_client_stats:` with a single colon misses submodules — match the prefix.
7. **A blocked backgrounded app fails DNS**, so the error is `UnknownHostException`, not a connect
   failure. Don't read it as a DNS misconfiguration.
