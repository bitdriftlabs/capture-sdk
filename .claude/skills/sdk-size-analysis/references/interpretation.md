# Interpretation and Experiments

Start with the run's generated analysis packet. Setup, recovery and collection
belong to [the measurement procedure](measurement.md), not this reasoning phase.
Do not calculate report totals manually or search for new artifact paths.

## Denominators and Audits

- Raw file bytes include instructions, constants, tables, relocations, symbol/debug
  metadata, headers, and file/page alignment.
- Executable storage includes stubs: `.text` alone excludes `.plt`. Name the denominator.
- ZIP/IPA metrics include the complete fixed-entry archive and overhead. Compression
  is not additive across sections or categories; ordering and shared patterns matter.
- ELF NOBITS and Mach-O zero-fill consume virtual memory, not file bytes. Table changes
  may affect raw size only at page boundaries. Keep instruction/data deltas visible.
- `.gnu_debugdata` is a shipped mini debug ELF, not full DWARF.

Full/shipped allocated ELF sections must have identical addresses, sizes, types,
flags and hashes, plus build IDs. Verify AAR and single-entry ZIP native identity.
Uploaded debug symbol names/addresses/sizes must match; allow only known debug-only
d/r-to-b kind changes. With debug-only analysis, verify layout and retained content,
and disclose that NOBITS allocated-byte identity is unverified.

The ELF analyzer sweeps intervals: aliases count once, ownership conflicts are explicit,
and gaps are assigned to no one. It partitions `.text` and adds other executable
sections. Future builds need not tile text exactly. Mach-O next-address estimates
include alignment/anonymous code and are not exact function sizes.

## Labels, Not Removable Costs

Rules choose JNI/schema references before SDK types, dependency types, standard
support and native names. Each interval has one label. `Vec<Proto>` drop glue is
not wire parsing; a Tokio wrapper specialized on logger types is not purely logging.
Shared LLVM outliners remain separate. Fat LTO without DWARF prevents exact inline
provenance. Inspect instructions and matching pinned source for the largest functions.

Named dependencies outside logger are separately accounted for, not extra bytes to
add to every caller. Static `bl` counts are call sites, not execution frequency. Do
not multiply callee bytes by them. Indirect calls, tail branches, inlined work and
imported dynamic implementations are not a complete dependency graph.

Runtime flags do not remove linked code. Deleting a direct dependency can leave a
transitive user intact. Narrow Tokio features, watcher backends, boxing, no-inline
helpers and borrowed models are hypotheses until matched builds and behavior checks
show benefits. Allocation reduction can increase specializations/code size. Measure
tradeoffs rather than inferring them from source structure.

## Experiment Ledger

Record exact source/dirty inputs, immediate and original baselines, hypothesis,
parity checks, raw/ZIP/text/data/metadata results, hashes, and keep/reject decisions.
Isolate one change where possible; explicitly label cumulative changes. Preserve
rejected artifacts without leaving them enabled. Equal ZIP sizes do not prove identity.

Dated reports need source pins, build controls, complete accounting, hotspots,
evidence hashes/paths, measured versus proposed work, and limitations. Historical
proto experiments are separate from baseline findings. Do not claim CPU, allocations,
throughput, device execution, Linux CI equality, or signed thinning without measurements.
