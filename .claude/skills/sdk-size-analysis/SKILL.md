---
name: sdk-size-analysis
description: >-
  Deep capture-sdk native code-size analysis. Use for Android SO/AAR and iOS
  framework/app size, raw versus compressed size, ELF/Mach-O sections, protobuf
  footprint, LTO attribution, dependency and logger hotspots, symbol/debug-map
  audits, stripped-app validation, and matched baseline/candidate experiments.
---

# SDK Size Analysis

Use the standalone capture-sdk checkout. Read [repository guidance](../../../AGENTS.md).
The scripts own setup, builds, output discovery, arithmetic, checks and publication;
spend reasoning on source/disassembly interpretation and experiments, not getting numbers.
Reports are dated evidence, never inputs to a fresh measurement.

## Measure

From the standalone SDK root:

```sh
python3 .claude/skills/sdk-size-analysis/scripts/measure.py
```

This checks Python dependencies/tests, macOS/Xcode/JDK 17, creates a clean detached
worktree at HEAD, and builds/audits Android ARM64 plus the iOS framework and unsigned
stripped sample. It prints the run directory, resume command, verified totals and
analysis-packet path. User checkouts/dependency wiring stay untouched; everything
temporary remains under `.tmp/`. Do not construct build commands or guess outputs.

Use `--revision <commit>` for another committed variant, `--report-prefix docs/<name>`
for durable publication, and `--baseline <baseline.evidence.json>` for computed
deltas with control mismatches flagged. No baseline is selected implicitly.
Use `--check-only` to test setup without building.

## Recover or Publish

```sh
python3 .claude/skills/sdk-size-analysis/scripts/measure.py --run .tmp/sdk-size-analysis/<run>
python3 .claude/skills/sdk-size-analysis/scripts/measure.py --run .tmp/sdk-size-analysis/<run> \
  --publish-only --report-prefix docs/<name>
```

Fix the first reported failure, then use the printed resume command. Completed
stages are hash-checked and skipped; partial artifacts/logs are archived before
retry. Changed scripts/toolchains require a new run. Never clear caches or reset
checkouts to recover. Publication checks both platform receipts and all artifact
hashes without building. Analyst text outside generated markers survives updates;
different measured binaries with existing analysis require a new report prefix.

## Interpret

Read the run's `results/ANALYSIS.md`, then
[interpretation rules](./references/interpretation.md). The packet links logger
disassemblies and pinned logger source, and tabulates static Android call sites,
reflection matches and iOS hotspots. Complete call-site accounting remains linked.
Inspect those and matching source before proposing optimizations. Whole-function
labels under fat LTO are not additive removable costs; static calls are not execution
frequency. Keep raw, ZIP, instructions, constants, metadata and alignment distinct.

Write evidence-backed reasoning only below `## Analyst Interpretation` in the
published report. Do not hand-edit generated numbers. State measured versus proposed
opportunities, behavior tradeoffs and unverified claims. Source changes need normal
format/lint/behavior/ABI checks; tooling-only work does not need unrelated SDK tests.
When replacing a historical report, revalidate the migrated interpretation and replace
its historical warning with current findings; the archived report remains historical.

## References

- [Canonical measurement procedure and controls](./references/measurement.md):
  prerequisites, staged runs, preservation, metrics, legacy and exceptional paths.
- [Interpretation and experiment ledger](./references/interpretation.md): attribution
  caveats and the reasoning required after collection.
- [Historical baseline](../../../docs/sdk-code-size-2026-10-08.md): explicitly dated
  evidence, not a substitute for a fresh run.
