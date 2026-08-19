# Stats flush matrix — current state, assumptions, open questions

Snapshot for picking this work back up. Written 2026-08-19 against **shared-core `5f0f7b29`**
(`main`, after PR #1107 merged) on a **Pixel 10 / API 37**.

Two things landed at once and both invalidate older notes: an explicit flush now **re-anchors** the
periodic schedule, and this account pushes **`stats.disk_flush_interval_ms = 5s`** (was 30s).

## Verified behaviour

**Cadence.** First disk write + upload at **t+5.7s** (per *process* start, not per install). Then disk
every **5.00s** (gaps 4.99–5.01), periodic upload every **60.00s**.

**An explicit flush re-anchors the schedule.** This is the change to know about; older docs asserted
the opposite. `reset_after_explicit_flush()` → `start_recurring_cycle()` re-anchors both deadlines at
the flush:

```
disk: 5.69 … 95.70, 100.70 │ 102.67 FORCED │ 107.84, 112.71 …
ON_STOP t+102.64 → forced flush t+102.67 (+30ms)
next disk   forced +5.17s
next upload forced +60.04s
```

The old anchor predicted a tick at t+125.71 that never came.

**Read the reset values as derived, never fixed.** `start_recurring_cycle()` sets
`first_upload_pending = false`, so it uses the *recurring* intervals and never the 5s first-upload one:

- **upload** = `stats.upload_flush_interval_ms` (60s default, not pushed)
- **disk** = `effective_flush_interval(disk_flush_interval_ms, upload_flush_interval_ms)`

With disk 5s pushed: `60 % 5 == 0` → 5s. On the earlier 30s config the same probe measured
**+30.03s / +60.08s**. Same rule, different input. A config of 45s would silently collapse the disk
cadence to 60s, so check the pushed value before predicting a number.

**Any explicit/platform flush resets it**, not just `ON_STOP` — window-focus-loss flushes (see
`murki/flush-kill`) go through the same path.

**The upload floor is unarmed at launch.** `last_flush_upload_time` has exactly one assignment site
(the `EVENT_TRIGGERED` flush path), so periodic and handshake uploads never arm it, and shared-core
`184c3229` removed the workflow trigger that used to arm it seconds after start. Two backgroundings
inside 30s still trip it.

## The mistake worth not repeating

The historical 35s foreground wait was doing **two** jobs. I checked only one.

1. clearing the 30s upload floor — no longer needed, the floor is unarmed at launch
2. **letting the startup upload finish before backgrounding** — still needed

I cut the wait to 8s on the strength of (1) alone. The full sweep then produced **9 of 15 scenarios
at `ENQ/NONE`**, including ones that had passed hours earlier. Arithmetic:

```
startup upload dispatches ~5.7s
ack needs 3.3–6.8s        → lands 9.0–12.5s
8s pre-wait backgrounds at ~9.3s   ← ack still in flight
network cut ~4.9s later            ← kills startup AND backgrounding upload
```

Scenarios where the startup ack landed before backgrounding passed; the rest didn't. **18s verified as
sufficient** (`T02-back` and `T11-freezer` both `ENQ/NONE` → `ENQ/OK`).

Generalisation: before shortening a wait, enumerate everything it is load-bearing for. A wait that
looks like it guards one invariant may quietly be guarding two.

## Possible real regression — not test noise

Ack latency has degraded ~5× and it tracks the cadence change:

| config | ack latency |
|---|---|
| 30s disk | **0.6–1.4s** |
| 5s disk, short check | 2.3–3.6s |
| 5s disk, full sweep | **1.6–6.8s** (n=11) |

This matters beyond the harness: the OS firewall cuts background network **~4.9s** after `ON_STOP`
(HOME; ~3.9s for BACK). At a 6.8s ack the backgrounding upload **structurally cannot land**,
regardless of test timing. Worth deciding whether that is acceptable product behaviour before tuning
any more waits around it.

Second consequence: the disk-flush **coalescing branch is now reachable in ordinary runs** (5 of 88
windows in the full sweep; previously 0 without `scripts/force_coalesce.py`). A `coalesced > 0` row is
now normal.

## Current scenario set — 16, ~10.8 min

Runtime is dominated by two waits per scenario. The post-backgrounding tail is **15s**, which covers
the disk write (+30ms), the ack, the network cutoff (~5s) and the re-anchored flush (+5s). The old 40s
was sized for a 30s cadence that no longer exists.

| Group | Scenarios |
|---|---|
| Backgrounding method | `T01-home` `T02-back` `T03-recents` `T04-screen-off` |
| Restrictions | `T05-battery-saver` `T06-doze-deep` `T07-doze-light` `T08-data-saver` `T09-standby-restricted` `T10-bg-restricted` `T12-airplane` |
| Exits | `T11-freezer` `T13-am-kill` (kill at `ON_STOP`+~390ms, the side that reliably tests something) |
| Timing | `timing-cadence` (70s foreground — the minimum window showing two 60s-apart uploads) · `timing-reset-after-flush` · `timing-double-background` |

**`T03-recents` reporting `no reference event` is the expected result** — the activity stays started so
`ON_STOP` never fires. `T04`/`T06`/`T07` use screen-off and run last.

## Open items

1. **Pre-wait is still 8s in the committed scenarios.** 18s is verified but not applied — the right
   value depends on where ack latency settles, so it was left for a decision.
2. **The full sweep is not a valid baseline.** Its 9 `ENQ/NONE` rows are harness artifacts, not SDK
   behaviour. Re-run after the pre-wait decision.
3. `flush-matrix.md` still records "acks 0.6–1.4s" in places; that was the 30s config.
4. Ack-latency regression above — product question, not a harness one.

## Commands

```bash
S=.claude/skills/android-launch-run/scripts
python3 $S/selftest.py                              # patterns still match (no device)
CARGO_BAZEL_REPIN=true ./platform/jvm/gradlew -p platform/jvm :gradle-test-app:installDebug
python3 $S/sweep.py --out /tmp/sweep --serial <id>   # all 16, screen-off last
python3 $S/check_signatures.py /tmp/sweep/T01-home/<id>/logcat.txt
```

`CARGO_BAZEL_REPIN=true` is needed on the first Bazel run after a rev change — and note env vars do
**not** reach an already-running Gradle daemon. Confirm the library is current with
`strings <libcapture.so> | grep shared-core.git_`, which carries the rev; matching file sizes are not
proof.
