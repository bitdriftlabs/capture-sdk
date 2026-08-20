# Designing a run that measures what you asked

Subject-agnostic method. Read this before a new investigation; read the subject file
(`stats-flush.md`, `window-focus-flush.md`, …) for what the signals mean in that area.

Every lesson here came from a run that looked clean and measured nothing.

- [Structure of an investigation](#structure-of-an-investigation)
- [Attribute before you explain](#attribute-before-you-explain)
- [Reading a missing signal](#reading-a-missing-signal)
- [Races vs hard blocks](#races-vs-hard-blocks)
- [What invalidates a run](#what-invalidates-a-run)
- [Reporting](#reporting)

## Structure of an investigation

1. **State the question as a race or a presence/absence.** "Does the upload land before the OS cuts
   the network?" and "does anything run at all when the app switcher opens?" are answerable. "Is
   backgrounding handled correctly?" is not.
2. **Pick the reference event** that splits before from after. Wrong choice here is the most common
   way a run measures nothing — see SKILL.md. If the behaviour happens while the app is still
   visible, `process ON_STOP` will never fire and every "after" number will be empty.
3. **Establish a positive control in the same capture.** A foreground settle wait that produces one
   normal cycle of the work proves the detector is alive in *this* run, so a later zero means
   something. Without it, a zero is unfalsifiable.
4. **Run the unrestricted baseline first.** Every restricted result is meaningless except in contrast
   to it: "no upload under doze" says nothing until "acked in well under a second without it" is on
   the same device, same build, same session.
5. **Vary one thing.** The bundled matrix is built this way — one group varies the backgrounding
   method with restrictions off, another varies the restriction with backgrounding held constant.
6. **Read the raw capture before trusting a summary.** Summaries encode the parser's assumptions;
   the capture is the only artifact that cannot be wrong.
7. **Re-run anything surprising.** Sub-second orderings shift with device load, and a single run
   cannot distinguish a mechanism from a coincidence.

## Attribute before you explain

The same work looks identical in the log regardless of what asked for it, so attribution has to come
from a marker rather than from timing.

- **Platform-triggered** work carries a platform marker (for flushes, `state flushing initiated` from
  `bd_logger`). The underlying entry point is reachable only from the JNI/Swift bridge, so the marker
  means the app-side code asked.
- **Internally triggered** work has no marker. Either a timer fired — check whether it lands on
  `anchor + period·k` — or something inside shared-core raised it.
- **An off-schedule event with no platform marker is the interesting case.** Widen the filter
  (`bd_workflows=debug` for server-driven work) rather than theorising; server configuration can
  raise work whose timing depends on which rule matched, which reads as inexplicable until the filter
  is wide enough to show it.

Corollary that has burned this skill more than once: **"forced" is not "platform-triggered."** A
subsystem log saying a flush was forced tells you the flush path ran, not who asked. A pure
foreground run with no app-side call at all has produced several "forced" flushes.

## Reading a missing signal

A missing line has three very different causes, and they are indistinguishable in a summary:

| Cause | How to tell | What it means |
|---|---|---|
| **The detector is dead** | `check_signatures.py` reports the family `UNSEEN` while the run should have produced it | Harness bug. Fix before concluding anything. |
| **The prerequisite never happened** | the reference event or the transition itself is absent from the capture | The run didn't reach the state under test. Usually a scenario or device-state ordering problem. |
| **The behaviour genuinely didn't fire** | detector proven alive by a positive control in the same capture, transition present | A finding. |

The third only exists once the first two are excluded. A detector that has never fired in any capture
is not evidence of anything — it is an untested pattern.

**A missing consequence is not automatically a block.** Work already in flight from before the
reference event competes with the work under test and can die with it at a network cutoff, which makes
the result *inconclusive* rather than negative. `parse_logs.py` reports `startup_upload_in_flight` for
exactly this case and `sweep.py` flags those rows; skipping that check once turned a full sweep into
nine apparent regressions that were nothing of the kind.

**A skip is not a failure.** Subsystems deliberately decline work when the data is already safe or a
floor hasn't elapsed. Reporting those together with real blocks hides the difference between *blocked*
and *intentionally skipped* — read the reason text, not just the absence.

## Races vs hard blocks

Classify the mechanism before treating any device-to-device difference as a finding:

- **A race** is decided by whether the work completes inside a window that the OS is about to close
  (network revocation on backgrounding, a freeze, a process kill). Faster hardware, a warmer cache or
  a shorter RTT changes the winner, so **divergence between devices is real and expected**, and a
  single run proves little.
- **A hard block** is a rule already in force when the work runs — a firewall entry, a permission the
  OS answers synchronously. There is no window to win, so **identical results across devices are
  expected, not a sign the emulator is unrealistic**. The useful statement is *why* there was no
  difference.

Timing distributions matter here. A typical latency with a heavy tail — a tight mode with rare
excursions an order of magnitude out — means the **median is stable and the mean is not**. Reading a
mean off a handful of opportunistically scraped samples has produced a "2x slower" regression report
that was purely tail artifact. If a number matters, measure it deliberately with repeated cycles, and
report the median with the spread.

The same tail sets a limit on scenario design: **no fixed wait can cover a tail**. Size waits for the
typical case, and treat the rare outlier as a per-run flag rather than inflating every wait.

## What invalidates a run

Check these before writing up anything:

- **The reference event fired before the action marker** — the app was already in the target state.
- **No positive control in the capture** — a zero can't be distinguished from a dead detector.
- **A restriction applied after the work ran** — backgrounding alone removes network within seconds
  and freezes the process within a minute, so a late restriction observes a corpse. Arm it before the
  reference event.
- **A device-level gate absorbed the run** — a rate floor, an anti-hammer interval or a debounce
  window can suppress the thing you were measuring. The gate is then what you measured; lengthen the
  preceding idle and repeat.
- **The install is stale** — the APK is whatever was last built. Switching branches or bumping a
  pinned dependency does not change the device.
- **The device was locked**, or re-locked mid-sweep by a screen-off scenario.
- **State left over from the previous run** — a consumed permission decision, a rotation, a raised
  buffer size. If run #2 of the same scenario differs from run #1, the scenario is not repeatable and
  neither result is trustworthy.

## Reporting

- **Quote the timeline, on the device clock**, with the reference event at zero. Relative offsets
  survive; wall-clock times don't.
- **Say which side of the reference each number came from.** A foreground success reported without
  that distinction reads as a backgrounding success.
- **Name the mechanism, not just the outcome.** "No upload under battery saver" is a fact; "battery
  saver installs a firewall rule already in force when the flush runs, so there is no window to win"
  is a diagnosis, and it predicts the next result.
- **Distinguish measured from inferred.** Anything not visible in the capture is inference; label it.
- **State what you did not cover.** A scenario that can only be driven manually, a branch reachable
  only by a purpose-built script, a row that needs hardware you don't have — say so, or the matrix
  reads as complete when it isn't.
- **Give measurements as shapes with the conditions attached** ("acks in a few hundred ms against a
  ~5s cutoff, on one phone with a fast RTT"), not as constants. Constants in documentation go stale
  silently; shapes stay useful and invite re-measurement.
