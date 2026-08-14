# Worked example: the stats-flush matrix

A 14-scenario study of what happens to **stats flushing** when an app is backgrounded, run on an
emulator (API 36) and a Pixel 10 (API 37). Read this either as a template for structuring a
multi-scenario investigation, or when the question is specifically about flush/upload behaviour.

Full data and per-run timelines live in `tools/flush-matrix/` (untracked) if it is still present.

## The question and why the first attempt failed

*"When the app is backgrounded, does its stats upload get out before the OS cuts network?"*

The first four runs all answered "no upload happened" — and all four were wrong. The
`ON_STOP` upload was being **debounced** by `stats.minimum_upload_interval_ms` (30s), because the
runs backgrounded the app ~5s after launch and the startup upload had already armed the timer. The
debounce masked every other variable.

**The fix that made the matrix meaningful:** idle in the foreground for ~35s before backgrounding.
This is the single most important thing to get right; without it you measure the debounce.

## Structure that worked

Three groups, so each isolates one variable:

1. **Backgrounding method** (no restrictions): HOME, BACK, recents, screen-off — does *how* the app
   leaves the foreground matter?
2. **Restriction modes** (HOME as the constant): battery saver, deep/light doze, Data Saver,
   standby-restricted, bg-restricted, freezer, airplane.
3. **Exit variants** (backgrounded first): `am kill`, `am force-stop`, JVM crash.

Two axes worth separating explicitly in any results table: **which phase** (foreground vs
backgrounding) and **which artifact** (disk write vs upload). Conflating them is the easiest way to
misread results — a foreground disk write succeeding says nothing about the backgrounding flush.

## Findings

**Plain backgrounding does not block the upload.** Unrestricted, the `ON_STOP` upload was acked in
**475ms–2241ms** against a network cutoff of **3.8–5.1s**. Every unrestricted run passed on both
devices.

**Battery saver and doze do block it** — enqueued, never acked, on both devices. The socket stayed
nominally open for another ~4.9s after the enqueue with no response, while the same run's
foreground upload acked in ~600ms. So these bite *before* the ~5s firewall deadline; a different,
earlier mechanism.

**Data Saver and standby-restricted diverged by device**: blocked on the emulator, succeeded on the
Pixel (703ms / 615ms) with the restrictions verifiably in effect. Best explanation is that the
Pixel's faster upload slips through before the restriction gates the socket — a race the fast
device wins, not immunity.

**Recents never fires `ON_STOP` at all**, so no backgrounding work runs. Whole-run lifecycle was
`ON_CREATE`/`ON_START`/`ON_RESUME` only, and the firewall allow was never revoked either. But
window focus *did* drop, ~334ms in — which is why focus is the only way to observe this state.

**A stalled upload wedges the stats flusher.** The sharpest finding. In airplane mode, with an
earlier upload outstanding and undeliverable, the `ON_STOP` flush produced **no disk write at
all** — no `writing snapshot`, no `stats flushed`, and not even
`flush already in progress, skipping`. `state flushing initiated` fired, so the request reached
shared-core and then vanished. Reproduced on both devices.

Note what makes that conclusive: `writing snapshot` fires on the timed path too, and the same run
proves it (a timed flush wrote its snapshot 34s earlier). So its absence after `ON_STOP` means the
write genuinely did not happen. An earlier version of this finding rested on missing
`stats flushed`, which is forced-path-only and therefore proved nothing.

**Kill, force-stop and freeze do not beat the upload.** All fired at ~+3.7s; uploads had already
acked at +552–997ms, and the disk write is durable at ~+100ms.

## Timing constants (both devices)

| Hop | Value |
|---|---|
| user action → `process ON_STOP` | 1227–1564ms (`ProcessLifecycleOwner` debounce); screen-off on real hardware 2258–2290ms |
| `ON_STOP` → forced flush | 9–163ms |
| forced flush → `writing snapshot` | 6–40ms |
| upload enqueue → ack | 475ms–2241ms (or never, under restriction) |
| `ON_STOP` → network cutoff | HOME 4.95–5.00s · BACK 3.77–3.88s · screen-off 4.30–4.56s |

BACK is reproducibly ~1.1s earlier than HOME on both devices, so the "~5s budget" is HOME-specific.

## Mistakes that cost real time

Worth reading before designing a matrix of your own:

1. **The 30s debounce** invalidated the first four runs, and the failure looked like a real finding.
2. **A locked phone** silently invalidated six runs. The app launched behind the keyguard and
   backgrounded itself during the foreground wait, so the action landed on an already-backgrounded
   app. The rows looked plausible and contradicted the emulator. Detect by comparing the reference
   event against the action marker.
3. **Screen-off tests re-lock the device**, invalidating everything after them — order them last.
4. **`am set-standby-bucket` is undone by launching the app**, so that row measured nothing until
   the mode moved to after-backgrounding.
5. **Reading the wrong shared-core tree.** Log text came from the rev pinned in `Cargo.toml`, not
   the local `../shared-core` checkout, and the two differed.
6. **Grepping `bd_client_stats::stats` only**, which misses `::file_manager` — and
   `writing snapshot` lives there. This is why the disk-write signal was missed entirely at first.
   Match the `bd_client_stats*` prefix.

## Not covered

The JVM-crash scenario (the only path that runs `flush(blocking=true)`, with a hardcoded 500ms JNI
wait) needs UI interaction to trigger — select "JVM crash in background" in the app's App
Terminations card — so it was never automated. Given observed ack latencies exceed 500ms, the
blocking flush would likely time out; that remains untested.
