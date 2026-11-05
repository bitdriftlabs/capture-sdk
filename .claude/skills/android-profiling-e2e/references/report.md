# Writing the report

The reader is someone who wasn't in the session: an SDK or backend engineer deciding what to fix.
They need conclusions they can act on, the evidence for each, and nothing that assumes context
they don't have.

## Writing rules

- **State conclusions, not history.** "Every result is marked partial even though no data was
  lost", not "every result is still marked partial". Avoid "still", "again", "unchanged", "as
  before", "last run", "fixed", "recheck" unless the user explicitly asked for a before/after
  comparison. Then give both numbers and say what changed.
- **Every gap and note ends with a suggestion**: what to change, and where (file and line when
  known, otherwise the component). The reader should never have to ask "what proposal?".
- **Every finding names its evidence**: the cell(s), session links and artifact IDs it comes from.
- **Measured vs inferred.** Numbers come from the trace or the scripts. Mark anything you computed
  yourself, like the outer-frame counts, as your computation, not backend behavior.
- **Classify against the trace_processor baseline.** Each finding says whether it is bitdrift only,
  the same in trace_processor, or an environment effect (device class). That decides who fixes it.
- **Name things the reader recognizes**: "heap profile, Java allocations on" rather than `HPT`
  (put the cell ID next to it once). Explain an R8 or Perfetto concept in one sentence the first
  time it appears.
- **Title**: what was tested, e.g. "Release JVM Profiling E2E". Not "recheck" or "round 2".

## `report.md` structure

```markdown
# <What was tested> — <date>

<2–4 sentence summary: what ran, the headline result, the main gap.>

Environment: device/model/OS · app id + version name (code) + variant · capture-sdk commit · backend host · workflows

## Results

| cell | type | trace bytes | integrity | correctness (key numbers) | session | artifact |
|---|---|---|---|---|---|---|

## Findings

### <Conclusion as a sentence> — pass | gap | note
<What was measured and why it matters, with numbers.>
**Evidence:** <cell> — [session](timeline-url) · artifact `<id>`
**trace_processor:** bitdrift only | same | bitdrift better | n/a
**Suggestion:** <concrete change and where>   ← required for gap and note

## Compared with trace_processor

| result | trace_processor | bitdrift | verdict |

## Method

<numbered steps: triggers, commands and args, compile-mode reset, downloads, verifiers>
<table: cell · session link · artifact id · raw bytes · processed bytes>

## Device and account state after the run

<temp workflows (stopped, with links), paused workflows restored, compile mode, rate limiter>
```

Order findings by importance: correctness failures, then gaps, then notes, then passes.

## HTML briefing (only when asked)

Build it from `report.md`, with the same content and order, as a published artifact (follow the
Artifact tool's design guidance). On top of the markdown it should have:

- a results-at-a-glance strip (3–4 numbers with pass/gap chips) and a matrix of cells
- a marked "Suggestion" box at the end of every gap/note finding
- links: session timeline URLs, workflow URLs, and download API routes shown as copyable text,
  with a note that they need a signed-in bitdrift token
- the trace_processor comparison table

Traces can't be embedded or opened in Perfetto UI from the artifact, because artifact pages can't
host `.tar` files and Perfetto can't fetch authenticated URLs. Say how to open them instead:
download through the API, then drag into ui.perfetto.dev.
