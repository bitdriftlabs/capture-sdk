# Log signatures

Every line worth matching, what it proves, and what it does **not**. All timestamps are on the
device clock, so sources interleave correctly in a single `-b main,events` capture.

- [1. App-side lifecycle](#1-app-side-lifecycle-bitdrift-lifecycle)
- [2. OS-side lifecycle](#2-os-side-lifecycle-events-buffer)
- [3. Flush attribution](#3-flush-attribution-bd_logger)
- [4. Stats](#4-stats-bd_client_stats)
- [4b. Disk-flush debounce window](#4b-the-disk-flush-debounce-window-c3ba1cba)
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

> ⚠ **This subsystem was substantially reworked in shared-core `c3ba1cba`** (the "more stats
> changes" bump). Most message text changed, two lines disappeared, and several new ones appeared.
> `scripts/parse_logs.py` matches **both** wordings, because a pattern that silently stops matching
> reports *"no upload happened"* rather than *"I can't see it"* — the worst failure mode available
> here. It cost a false regression report the first time.

### Message text by rev

| Meaning | `c3ba1cba` and later | up to `42637e1f` |
|---|---|---|
| Forced flush started | `flushing collected stats to disk` | `received a signal to flush stats to disk` |
| Merge before write | `updating aggregated snapshot file with N metrics…` | *(same)* |
| **Disk write** | **`writing snapshot: stats_uploads/<uuid>`** | ***(same)*** |
| Upload staging | `preparing stats upload from disk: only_if_file_is_old=<bool>, reason=<REASON>` | `processing upload from disk` |
| Upload enqueued | `prepared <REASON> stats upload: uuid=<uuid>, snapshots=N, metrics=N` | `sending pending flush upload: <uuid> with N metrics` |
| Upload dispatched | `dispatched <kind> stats upload for N source files` | *(no equivalent)* |
| Upload result | `<kind> stats upload completed: uuid=<uuid>, success=<bool>, source_files=N` | `stat flush upload attempt complete: UploadResponse { success: <bool>, uuid: "<uuid>" }` |
| Upload suppressed | `skipping <kind> stats upload: minimum upload interval has not elapsed` | `skipping flush upload, minimum interval not elapsed` |
| Flush dropped | `flush already in progress, skipping` | *(same)* |
| Forced flush completed | *(gone)* | `stats flushed` |
| `flush_to_disk()` entered | *(gone)* | `processing flush to disk tick` |

`<kind>` / `<REASON>` observed so far: `explicit flush` / `UPLOAD_REASON_EVENT_TRIGGERED`,
`handshake`, `periodic`. **Match the kind loosely** — it is an open set, and pinning it to
`periodic` alone caused a real misread (an explicit-flush suppression was reported as "no upload
attempted" instead of "debounced").

### Why an upload was *not* attempted — three distinct reasons

Only the first is a limitation; the rest are deliberate. Reporting them all as "no upload" hides
the difference between *blocked* and *intentionally skipped because the data is already safe*.

| Line | Means |
|---|---|
| `skipping <kind> stats upload: minimum upload interval has not elapsed` | **Debounced.** Backgrounded too soon after the last upload; the run measured the debounce, not the question. Seen with `<kind>` = `periodic` **and** `explicit` |
| `skipping explicit stats upload: another flush upload is active; stats are durable` | **Coalesced.** An upload is already in flight. *"stats are durable"* is the code stating the disk write is safe regardless — this is what airplane mode now produces, replacing the pre-bump behaviour where the flusher wedged and nothing reached disk at all |
| `skipping periodic stats upload: another periodic or deferred upload is active` | As above, periodic path |

New in `c3ba1cba`, no pre-bump equivalent:

- `started stats disk flush debounce window: duration=<D>` / `stats disk flush debounce window
  closed without a trailing flush` — see §4b
- `handshake stats upload completed: …` — stats now upload on API handshake, not only on flush
  and timer
- a durable pending-upload index: `initializing pending aggregation index`,
  `creating new snapshot in index: <uuid>`, `marking entry as ready to upload: <uuid>`,
  `deleting pending upload: <uuid>`, `no pending upload: index is empty` /
  `no pending upload: file is not old enough`

### Which line proves a disk write

Use **`writing snapshot`**. It is the file write, it fires on both the forced and timed paths, and
it is one of only two stats signals that survived the rework unchanged.

`stats flushed` is *not* the disk-write proof, and on `c3ba1cba` it no longer exists at all. On
older revs it appeared only on the forced path, so a timed flush wrote to disk while logging
nothing of the sort. Reasoning from its absence produces a false "no disk write" conclusion — this
happened, and cost a wrong finding until `writing snapshot` was added to the patterns.

### Classifying a flush

| Kind | Signature |
|---|---|
| Platform-triggered | `state flushing initiated`, then a forced-flush line within ~300ms |
| Internal forced | forced-flush line with **no** preceding `state flushing initiated` |
| Timed | on `c3ba1cba` the forced/timed wording is shared, so lean on `state flushing initiated` to separate them; pre-bump, a `processing flush to disk tick` with no `received a signal…` just before |
| Dropped | `flush already in progress, skipping` |

### Correlating uploads

Match the enqueue to its result **by uuid, never by order**. Acks arrive out of order, can take
475ms–17s, and may reference an upload from a *previous process run* recovered from disk at startup.
Both revs carry a uuid on both lines; only the surrounding text differs.

## 4b. The disk-flush debounce window (`c3ba1cba`+)

A 1s window opens immediately **after** each disk write:

```
flushing collected stats to disk
writing snapshot: stats_uploads/<uuid>
started stats disk flush debounce window: duration=1s
  … 1.003s later …
stats disk flush debounce window closed without a trailing flush
```

Its purpose is to coalesce a second flush arriving inside that span into a single write.

**Validating it is awkward**, because forcing a coalesce needs two flushes landing under 1s apart,
which is not reliably schedulable from adb. `parse_logs.py` therefore checks the invariant the
window implies — consecutive `writing snapshot` events should never be closer together than the
window — and reports:

```
DISK-FLUSH DEBOUNCE (1000ms window): 5 opened · 5 closed empty · 0 coalesced ·
  5 write(s), closest 1918ms apart => invariant HELD
```

Across 8 runs on two devices (36 windows, 36 writes) the closest pair was **1743ms** and the
invariant held everywhere — but **0 windows ever coalesced**, so the coalescing path itself remains
untested. The parser says so explicitly rather than implying coverage it doesn't have. Exercising it
needs something that deliberately issues two flushes inside 1s.

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

1. **Signatures drift with the pinned shared-core rev.** The `c3ba1cba` bump renamed most stats
   messages; the first run against it reported *zero uploads on both devices*, which looked like a
   serious regression and was purely stale patterns. Before believing any negative result, confirm
   the signature still exists in the capture. A missing pattern and a missing behaviour are
   indistinguishable from the summary alone — always check the raw log for the *shape* of what
   happened.
2. **`stats flushed` is not a disk-write signal** — forced path only, and gone entirely on
   `c3ba1cba`. Use `writing snapshot`.
3. **`processing flush to disk tick` fires on forced flushes too** (pre-bump) — don't count it as
   "timed".
4. **Match the upload `<kind>` loosely.** `skipping periodic stats upload` and
   `skipping explicit stats upload` are both suppressions; matching only one reports the other as
   "no upload attempted", which reads as a bug rather than intended behaviour.
5. **"Forced" ≠ platform-triggered** — use `state flushing initiated`.
6. **Correlate uploads by uuid**, not order; acks can be seconds late or from a prior process.
7. **The minimum-upload-interval debounce hides everything else.** Background sooner than 30s after the last
   flush-triggered upload and the upload is suppressed before network or device state matters. A
   run showing any `skipping … upload … minimum … interval` line measured only the debounce.
   **Still true on `c3ba1cba`:** explicit flush uploads remain gated, verified by the
   `background-no-wait` scenario returning `DEBO` on both devices. The 35s foreground wait is not
   optional.
8. **Rust logcat tags are full module paths** (`bd_client_stats::file_manager`). Grepping
   `bd_client_stats:` with a single colon misses submodules — match the prefix.
9. **A blocked backgrounded app fails DNS**, so the error is `UnknownHostException`, not a connect
   failure. Don't read it as a DNS misconfiguration.
