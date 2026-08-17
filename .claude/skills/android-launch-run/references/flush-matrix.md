# Worked example: the stats-flush matrix

A 14-scenario study of what happens to **stats flushing** when an app is backgrounded, run on an
emulator (API 36) and a Pixel 10 (API 37). Read it as a template for structuring a multi-scenario
investigation, or when the question is specifically about flush/upload behaviour.

Run it yourself: `assets/scenarios/matrix/T01…T14.json`, one scenario per test.

**Latest full run: 28/28 on shared-core `c3ba1cba`.** An earlier 14-scenario pass was made against
`42637e1f`; where the two disagree, both are given, because the differences are the most useful part.

> ⚠ **`Cargo.toml` currently pins `42637e1f` — the *earlier* rev.** Check before relying on anything
> here: `grep -m1 'bd-client-common' Cargo.toml`. The headline "28/28" results below were measured on
> `c3ba1cba`, so on the current pin the **left**-hand/"before" side of every rev comparison is the
> live behaviour. Two concrete consequences:
>
> - **The airplane wedge (§ below) is present again**, since `c3ba1cba` is what fixed it. Treat
>   "flush produces no disk write in airplane mode with a stalled upload" as expected on this pin,
>   not as a new regression.
> - **The disk-flush debounce window does not exist**, so `parse_logs.py` prints no
>   `DISK-FLUSH DEBOUNCE` line. Confirmed by `check_signatures.py` reporting `stats-debounce` as
>   `UNSEEN`. That absence is the rev, not a fault.
>
> Re-verified on `42637e1f` and still true: the **35s foreground wait is required** (the
> `stats.minimum_upload_interval_ms` floor is 30s and is not overridden by runtime config), **recents
> never fires `ON_STOP`**, and **deep doze blocks the upload identically on emulator and phone**.

## The question

*"When the app is backgrounded, is there enough time for stats to (1) flush to disk and
(2) upload?"*

**Answers, from 28 runs:**

1. **Disk: always yes, ~50ms after `ON_STOP`** — in every run, under every restriction including
   airplane mode. Roughly 100× headroom against the ~5s budget. Nothing delayed or prevented it.
2. **Upload: usually, with +3.1s to +4.4s of margin** on unrestricted paths (acks 508–1080ms vs
   cutoffs 3.86–5.00s). Blocked by battery saver and both doze depths on both devices; blocked by
   Data Saver and standby-restricted on the emulator only.

## Why the first attempt was wrong

The first four runs all answered *"no upload happened"* — and all four were wrong. The `ON_STOP`
upload was **debounced** by the minimum upload interval, because the runs backgrounded ~5s after
launch and the startup upload had already armed the timer. The debounce masked every other variable.

**The fix that made the matrix meaningful: idle ~35s in the foreground before backgrounding.** This
is required on **both** revs — verified by `assets/scenarios/background-no-wait.json`, which
deliberately skips the wait and returns `DEBO` on both devices. Re-confirmed on `42637e1f`: the gate
is `stats.minimum_upload_interval_ms`, default 30s, and it is *not* among the flags the runtime
pushes. The 5s figure in the `does not cleanly divide active upload interval 5s` line is the upload
*cadence*, a different knob — mistaking it for the gate is the obvious way to shorten these waits and
silently invalidate the whole matrix again.

## Structure that worked

Three groups, each isolating one variable:

1. **Backgrounding method** (no restrictions): HOME, BACK, recents, screen-off
2. **Restriction modes** (HOME constant): battery saver, deep/light doze, Data Saver,
   standby-restricted, bg-restricted, freezer, airplane
3. **Exit variants** (backgrounded first): `am kill`, `am force-stop`, JVM crash

Two axes must stay separate in any results table: **which phase** (foreground vs backgrounding) and
**which artifact** (disk write vs upload). Conflating them is the easiest way to misread everything
— a foreground disk write says nothing about the backgrounding flush.

## Findings (`c3ba1cba`, 28 runs)

**Unrestricted backgrounding uploads fine.** HOME/BACK/screen-off all acked in 508–1080ms against
cutoffs of 3.86–5.00s. Every unrestricted run passed on both devices.

**Battery saver and doze block the upload; the disk write still lands.** Six runs (battery saver,
deep doze, light doze × 2 devices): enqueued, **never acked**, while the firewall revoke still shows
its usual ~4.3–5.0s. So these bite *earlier and by a different mechanism* than the firewall. Doze
agrees closely across devices — deep 4.30s vs 4.33s, light 4.28s vs 4.34s.

**Data Saver and standby-restricted diverge by device.** Blocked on the emulator, succeeded on the
Pixel (549ms, 828ms), with the restrictions verifiably in effect. Best explanation: the Pixel's
faster round trip slips through before the restriction gates the socket — a race the fast device
wins, not immunity. This divergence is why cross-device agreement elsewhere (doze) is worth stating.

### Whether devices diverge is predictable: races diverge, hard blocks don't

Use this before treating any emulator/phone difference as a finding:

| Mechanism | Shape | Cross-device |
|---|---|---|
| Data Saver, standby-restricted | **race** — the restriction gates the socket around the time the upload flies | **diverges**; the faster device wins |
| deep/light doze, battery saver | **hard block** — a firewall rule already in force when the flush runs | **identical**; there is no window to win |

Doze is the clean case: `force-idle` lands ~1.1–1.2s *before* `ON_STOP` (the `ProcessLifecycleOwner`
debounce), so the block is established before the flush starts and both devices behave the same —
deep 4.30s vs 4.33s, and re-confirmed on `42637e1f` with cutoffs within 16ms. **Identical results on
a hard block are expected, not evidence the emulator is unrealistic**, and reporting them as
"no difference found" undersells it: the useful statement is *why* there was no difference.

**Recents never fires `ON_STOP`**, so no backgrounding work runs at all — unchanged across both
revs. The whole-run lifecycle is `ON_CREATE`/`ON_START`/`ON_RESUME`, and the firewall never revokes
either. Window focus *does* drop, which is the only way to observe the state.

**Kill, force-stop and freeze do not beat the upload.** All fired at +5s or +10s; uploads had acked
at 624–847ms and the disk write had been durable for ~5s.

### The airplane wedge — present on `42637e1f`, FIXED on `c3ba1cba`

The sharpest finding of the original study, and the clearest behavioural change between revs.

**Before:** in airplane mode with an earlier upload outstanding and undeliverable, the `ON_STOP`
flush produced **no disk write at all** — no `writing snapshot`, and not even
`flush already in progress, skipping`. `state flushing initiated` fired, so the request reached
shared-core and vanished. The flusher was wedged by the stalled upload.

**After:** the same scenario writes to disk normally and skips only the upload, saying so:

```
+5ms   state flushing initiated
+23ms  flushing collected stats to disk
+35ms  writing snapshot: stats_uploads/28dca247…            <- WRITE HAPPENED
+39ms  skipping explicit stats upload: another flush upload is active; stats are durable
```

The phrase *"stats are durable"* is the implementation stating the disk write is safe regardless.
Confirmed on both devices.

## Timing constants (`c3ba1cba`, both devices)

| Hop | Value |
|---|---|
| user action → `process ON_STOP` | ~1.26–1.5s (`ProcessLifecycleOwner` debounce) |
| `ON_STOP` → `state flushing initiated` | 4–7ms |
| `ON_STOP` → `writing snapshot` (durable) | **30–60ms** |
| upload enqueue → ack | 508–1080ms (or never, under restriction) |
| `ON_STOP` → network cutoff | HOME 4.92–5.00s · BACK 3.86–3.96s · screen-off 4.28–4.34s |

`back` cuts the budget by more than a second versus `home`, so "~5s" is a HOME-specific figure.
Roughly a quarter of the window is spent before the SDK is even told, because of the debounce.

## Mistakes that cost real time

Read these before designing a matrix of your own. Every one produced confident, wrong output.

1. **The upload debounce** invalidated the first four runs, and the failure looked like a real
   finding. Idle >30s in the foreground first.
2. **A locked phone** silently invalidated six runs — the app launches behind the keyguard and
   backgrounds itself during the wait, so the action lands on an already-backgrounded app. Detect by
   comparing the reference event against the action marker.
3. **Screen-off tests re-lock the device.** Order them last, and for a full sweep on a physical
   phone, disable auto-lock first — otherwise each one costs a manual unlock.
4. **`am set-standby-bucket` is undone by launching the app**, so that row measured nothing until the
   mode moved to after-backgrounding.
5. **Reading the wrong shared-core tree.** Log text comes from the rev pinned in `Cargo.toml`, not a
   local `../shared-core` checkout.
6. **Grepping `bd_client_stats::stats` only**, which misses `::file_manager` — where
   `writing snapshot` lives. Match the `bd_client_stats*` prefix.
7. **Stale patterns after a rev bump** reported *zero uploads on both devices*, which reads as a
   serious regression and was pure pattern rot. After any bump, treat a negative result as unproven
   until the signature is confirmed present in the raw capture.
8. **A missing reference event silently produced a false pass.** With no `ON_STOP`, the
   foreground/background split fell back to the first log line and counted foreground work as
   backgrounding work — T03 reported "uploaded in time" when no flush had run at all. The parser now
   refuses to emit a backgrounding verdict in that case.
9. **Derived artifacts outlive the bug that produced them.** `state.json` files written before that
   fix still carried the false pass, and regenerating a summary table from them reintroduced it.
   After any parser fix, re-derive from the **raw capture** — the logcat is the only artifact that
   cannot be wrong.
10. **Don't keep two copies of a report.** Editing the published copy and then re-copying from the
   working directory silently reverted verified corrections — twice, including rows that had already
   been checked. Generate reports from data into one canonical location, and if two copies must
   exist, write both in the same step and `diff` them.
11. **Assert your edits landed.** A `str.replace` that finds nothing is a silent no-op, so a
   "successful" patch script can change nothing at all. Assert the match, then grep the result —
   checking the same output you just wrote is not verification.

## Not covered

**T15, the JVM-crash scenario** — the only path that runs `flush(blocking=true)`, with a hardcoded
500ms JNI wait. It needs UI interaction (select "JVM crash in background" in the app's App
Terminations card), so it was never automated. Given observed ack latencies of 508–1080ms exceed
500ms, the blocking flush would likely time out; that remains untested.

**The disk-flush debounce coalescing path.** The 1s window (see `log-signatures.md` §4b) never
coalesced in 36 observed windows, so only its invariant is verified, not its merge behaviour.
