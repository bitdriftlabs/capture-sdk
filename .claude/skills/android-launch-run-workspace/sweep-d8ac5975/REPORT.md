# Stats flush/upload sweep — shared-core `d8ac5975` (PR #1107)

**Device:** Pixel 10, API 37, `5C050DLCR0001W` · **APK built** 2026-08-17 17:51:44 from `d8ac5975`
**Scenarios:** 16/16 completed, 0 errors · screen-off scenarios ran last
**Filter:** `info,bd_client_stats=debug,bd_logger=debug,bd_api=debug,bd_runtime=debug,bd_workflows=debug`

Raw `logcat.txt` per scenario is deliberately untracked (see `../.gitignore`). Each scenario
directory here holds the parsed `summary.txt` (timeline + verdict) and `state.json` (machine-readable
counts, device state after the run). Console output for the whole sweep is in `sweep-console.txt`.

## Results

| Scenario | ref | BG disk | upload | ack | netcut |
|---|---|---|---|---|---|
| `T01-home` | ok | YES | ENQ/OK | 1439ms | 4.996s |
| `T02-back` | ok | YES | ENQ/OK | 662ms | **3.896s** |
| `T03-recents` | **NONE** | — | *no reference event* | — | — |
| `T05-battery-saver` | ok | YES | **ENQ/NONE** | — | 4.958s |
| `T08-data-saver` | ok | YES | ENQ/OK | 976ms | 4.98s |
| `T09-standby-restricted` | ok | YES | ENQ/OK | 633ms | 4.993s |
| `T10-bg-restricted` | ok | YES | ENQ/OK | 1004ms | 4.979s |
| `T11-freezer` | ok | YES | ENQ/OK | 736ms | 4.926s |
| `T12-airplane` | ok | **YES** | NONE/NONE | — | 4.961s |
| `T13-am-kill` | ok | YES | ENQ/OK | 945ms | 1.324s |
| `background-no-wait` | ok | YES | **ENQ/OK** | 932ms | 4.991s |
| `timing-cadence` | ok | YES | ENQ/OK | 878ms | 4.94s |
| `timing-double-background` | ok | YES | ENQ/OK | 1322ms | 4.948s |
| `T04-screen-off` | ok | YES | ENQ/OK | 1535ms | 4.307s |
| `T06-doze-deep` | ok | YES | **ENQ/NONE** | — | 4.301s |
| `T07-doze-light` | ok | YES | **ENQ/NONE** | — | 4.279s |

Disk-flush debounce across the sweep: **64 windows opened, 0 coalesced.** Expected — ordinary
scenarios never land two flushes inside 1s. `scripts/force_coalesce.py` is the only way to reach that
branch; it does so reliably (3 coalesces per run).

## Findings

### 1. The airplane wedge is fixed — the headline result for this PR

`T12-airplane` produced **4 disk writes** plus the expected skip:

```
state flushing initiated
writing snapshot                                          (x4)
skipping explicit stats upload: another flush upload is active; stats are durable
```

On `42637e1f` this same scenario produced **no disk write at all** — `state flushing initiated` fired
and the flusher wedged behind the stalled upload. `NONE/NONE` in the upload column is correct here:
nothing was enqueued because an earlier upload was still in flight, and the log says so explicitly.

Evidence: [`T12-airplane/summary.txt`](T12-airplane/summary.txt)

### 2. `background-no-wait` is now inert, and the 35s wait no longer does anything

The scenario exists to confirm the `stats.minimum_upload_interval_ms` gate is active — it should
report `DEBO`. It reported `ENQ/OK`:

```
18:12:30.556  prepared UPLOAD_REASON_PERIODIC        <- the ~5s first upload
18:12:32.404  process ON_STOP
18:12:32.433  prepared UPLOAD_REASON_EVENT_TRIGGERED <- ALLOWED, only 1.88s later
```

`last_flush_upload_time` is armed **only by flush-path uploads**; a periodic upload does not arm it.
Until now the workflow flush trigger fired one early in every process, arming the gate — which is
exactly why the historical note says "the 30s debounce silently invalidated a whole matrix".
shared-core `184c3229` (in `d8ac5975`) removed that trigger, confirmed by **0** occurrences of
`uploading due to flush buffers action` anywhere in this sweep.

So a fresh process now reaches `ON_STOP` with the gate **unarmed**, and the 35s foreground wait in
every single-backgrounding scenario is dead weight — roughly **9 minutes of this sweep**.

The gate itself still works. `timing-double-background` arms it with a first `ON_STOP` upload, then:

```
18:18:26.599  prepared UPLOAD_REASON_EVENT_TRIGGERED   <- arms the gate
18:18:37.511  skipping explicit stats upload            <- refused, 10.9s later
18:18:49.709  skipping periodic stats upload
```

Evidence: [`background-no-wait/summary.txt`](background-no-wait/summary.txt) ·
[`timing-double-background/summary.txt`](timing-double-background/summary.txt)

### 3. Timing model holds unchanged on `d8ac5975`

| Quantity | Value |
|---|---|
| first disk write | t+5.71s (`stats.first_upload_flush_interval_ms` = 5s) |
| disk cadence | 30.00, 30.01, 30.01, 29.99 |
| upload cadence | 60.00, 60.00, 60.03 |
| debounce windows | 1.002–1.004s |

The forced-flush/tick pair reappears (`27.06s` then `2.94s`), reconfirming that a forced flush does
**not** reschedule the periodic timer — the next tick stays on the original anchor.

Evidence: [`timing-cadence/summary.txt`](timing-cadence/summary.txt)

### 4. Hard blocks block; races do not — and `T03-recents` still does nothing

`battery-saver`, `doze-deep` and `doze-light` all give `ENQ/NONE`: enqueued, never acked, disk write
unaffected. Doze deep and light agree within 22ms, consistent with both being firewall rules already
in force when the flush runs rather than races.

`T03-recents` reported **`NONE`** — zero `process ON_STOP`, one `window focus=false`. The activity
stays started while the overview is open, so no backgrounding work runs. That absence is the result,
not a failure, and `sweep.py` correctly refuses to print a verdict for it.

Evidence: [`T03-recents/summary.txt`](T03-recents/summary.txt) ·
[`T06-doze-deep/summary.txt`](T06-doze-deep/summary.txt)

### 5. `T13-am-kill` retiming helped but did not cross the boundary

Retimed from HOME+5s to HOME+2s to make the kill compete with the ack:

```
18:11:30.788  process ON_STOP
18:11:30.809  writing snapshot            (+21ms)
18:11:30.819  upload enqueued             (+31ms)
18:11:31.762  received ack for stats      (+974ms)
18:11:32.105  am_kill lands               (+1.32s)   <- upload won by 348ms
```

At the old +5s timing the upload won by 4.3s, so the scenario could not fail; now the margin is
348ms. But ack latency ranged **633–1535ms** across this sweep, so no fixed wait reliably lands
inside the window — probing that boundary needs two or three timings, not one.

Note the `netcut` column reads `1.324s` for this scenario rather than the usual ~5s: the kill itself
changes the uid's firewall state, so that number is measuring the kill, not the background revoke.

Evidence: [`T13-am-kill/summary.txt`](T13-am-kill/summary.txt)

## Open items

- **Decide the fate of `background-no-wait` and the 35s waits.** Dropping the waits reclaims ~9 min
  per sweep; keeping them is insurance if the workflow trigger ever returns.
- **`T13`'s boundary is unmeasured.** One run at HOME+1.2s and one at +1.6s would bracket it.
- **The coalescing branch is only reachable via `force_coalesce.py`**, not via any scenario. That is
  inherent — it needs sub-second scheduling that declarative steps cannot express.
