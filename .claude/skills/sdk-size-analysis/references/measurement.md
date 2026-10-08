# Measurement Procedure

This is the canonical procedure. [The skill](../SKILL.md) is the short entry point;
the scripts implement all routine mechanics. Do not duplicate their build commands
in reports or reconstruct output paths by hand.

## Prerequisites and Setup

Use macOS, full selected Xcode, JDK 17 and Python 3.11+. The SDK pins its Rust and
NDK through Bazel; do not install alternate compilers to match an old report.
The entry point verifies selected Xcode and strip/clang availability, selects JDK
17, creates/reuses a workspace-local venv, installs only missing/mismatched
[pinned packages](../scripts/requirements.txt), and runs tests/document-link checks.
Network access is needed for missing Python/Bazel dependencies. It does not prompt
for secrets, install Xcode/JDK or change the selected developer directory.

```sh
python3 .claude/skills/sdk-size-analysis/scripts/measure.py --check-only
python3 .claude/skills/sdk-size-analysis/scripts/measure.py
```

HEAD is the default SDK revision. Analysis tools run from the main checkout, while
measurement builds run in an automatically created clean detached worktree. Main
checkout edits are not silently included. `--revision <commit>` selects another
committed SDK variant; no branches, commits, pushes or resets are performed.
`--xcode-version <version>` requires that version to be selected already.
`--run .tmp/<name>` chooses a stable run location, or resumes one with a run receipt.

## Collection and Outputs

The [build/audit runner](../scripts/run_analysis.py) uses only the standalone
`./bazelw`, CI release flags, ARM64 and `--config=nocache`. Remote release caching
stays disabled; local action/disk cache hits are allowed. Never churn caches.
It resolves cquery outputs and structured aquery action inputs, then freezes
artifacts before changing platform configuration. NDK LLVM paths are resolved
before iOS builds can replace Bazel execroot symlinks.

- Android: AAR's exact `jni/arm64-v8a/libcapture.so`, full ELF when present,
  uploaded debug map/decompressed ELF, Bazel zipper `cC` single-entry archive,
  provenance, section/symbol partitions, logger source/manifest and disassemblies.
  If the full ELF is absent, audit retained debug symbols against the shipped
  instructions and explicitly mark NOBITS allocated-content identity unverified.
  Debug-only disassemblies restore labels from the audited symbol partition instead
  of objdump's misleading nearest exported JNI name; unresolved targets stay explicit.
- iOS: distribution static framework and archive/member checks, fresh
  `ios_app_size` device IPA, unmodified original executable, separately stripped
  `xcrun strip -u -r` copy, section/loader/symbol invariants, every real resource,
  and normalized IPA with exact entry/byte/CRC checks. The temporary empty profile
  workaround is bounded to the size target and the original BUILD is restored.
- Results: `results/ANALYSIS.md` contains computed totals and investigation links;
  `results/report.md` and `results/report.evidence.json` contain publication tables
  and compact evidence, including when `--report-prefix` also publishes a durable copy.
  The packet tabulates the twelve most frequent direct targets per Android logger
  hotspot, named reflection matches and iOS linked hotspots; full inventories are linked.
  Full artifacts and stage logs stay under the run directory.
  Setup/stage console logs are retained in a printed `setup-*` directory.
- Provenance: revision/clean status, source snapshots, dependency revisions,
  tool/version hashes, analysis-script hashes, exact commands, per-stage timestamps
  and every frozen artifact hash. Script changes during collection fail the run.

Raw Android bytes and the entire one-entry ZIP are separate metrics. Rounded-up
Android CI KiB is `(zip_bytes + 1023) // 1024`. The iOS framework metric is its ARM64
static binary, not the directory/distribution ZIP; CI KiB rounds down. Stripped
executable, bundle file bytes and complete normalized IPA are separate app metrics.
The unsigned sample is not installable, signed App Thinning or SDK-only app overhead.
Local macOS ARM64 results do not reproduce Linux/x86_64 Android CI bytes.

## Staged Runs and Recovery

The default runs Android then iOS as separately receipted stages. To split them:

```sh
python3 .claude/skills/sdk-size-analysis/scripts/measure.py --run .tmp/size-run --platform android
python3 .claude/skills/sdk-size-analysis/scripts/measure.py --run .tmp/size-run --platform ios
```

iOS requires the completed Android stage's Rust-demangling NDK tool record. The
second invocation publishes automatically. Split publication requires matching
SDK revision, source snapshots, script hashes, Xcode and host, and retains both
stages' command lists/timestamps/artifact hashes. Stage logs never overwrite each other.

On failure, fix the first error shown in the bounded log tail, then rerun using
the printed `--run` command. A completed stage is skipped only after its entire
artifact receipt and script lineage pass. Partial outputs/logs move to
`results/failures/` before retry; no failed evidence is destroyed or called current.
Only one process may use a run directory. Different scripts/toolchains/revisions
require a new run, not a patched receipt.

The guarded unsigned BUILD edit normally restores itself on exceptions. After a
hard process kill, resume restores it only if the saved bytes match the measured
commit and the current edit is exactly the known probe. Any other edit is preserved
and rejected. Manifests/locks/BUILD and clean status are checked before and after
collection. Do not reset a checkout to satisfy these checks.

## Publication and Comparison

```sh
python3 .claude/skills/sdk-size-analysis/scripts/measure.py --run .tmp/size-run \
  --publish-only --report-prefix docs/sdk-code-size-<date>
```

This reruns focused tests and audits frozen artifacts without builds or requiring
the previously selected Xcode. `--report-prefix` and `--baseline` are stored in the
run state for subsequent invocations. Legacy runs without `run.json` can use
[run_analysis.py](../scripts/run_analysis.py) with their explicit `--sdk-root`,
`--output`, `--xcode-version`, `--publish-only` and `--report-prefix` arguments.
Never mix retained legacy artifacts into a new collection.

Generated Markdown is marker-bounded. Analyst text outside the markers is never
replaced. It is bound to the SDK revision and measured binary hashes: changing
those with analyst text present requires a new prefix. An unmarked legacy report
is archived byte-for-byte beside it and recognized source/disassembly interpretation
is migrated with an explicit historical warning. Do not delete that archive until
the migrated reasoning has been checked. Publication records its own script hashes
separately from the original collection lineage.

Use `--baseline <compact.evidence.json>` for computed absolute/percentage deltas
in the analysis packet and `results/comparison.json`. Controls include dependency
pins, build flags/ABI, tool hashes, source config/lock snapshots and script lineage.
Any difference labels the comparison UNMATCHED; arithmetic is not a causal size claim.
No report date or baseline is hardcoded by the workflow.

## Exceptional Measurements

Dirty/local-wired candidates are not pristine committed variants. Preserve existing
SDK/dependency edits and package/API/schema parity; never automatically commit them
to satisfy this runner. Focused experts can use the [collector](../scripts/collect_android.py)
and [ELF analyzer](../scripts/elf_analysis.py) with `--help`, or the
[Mach-O analyzer](../scripts/macho_analysis.py) and
[strip validator](../scripts/validate_stripped_macho.py). These are diagnostic
interfaces, not the default route to numbers. Freeze both variants separately.
For shared-core wiring, direct SDK dependencies need path entries; keep necessary
protobuf patches, use `CARGO_BAZEL_REPIN=true` on the first changed-source build,
and verify package name/version parity from parsed lockfiles.

Never strip linkable static frameworks. Analyze them with the SDK-owned
[archive analyzer](../../../../ci/capture_ios_archive_breakdown.py).
When signing credentials and the actual CI metric are required, use
`//examples/swift/hello_world:ios_app_archive` and
[the signed export script](../../../../ci/ios_app_size_report.sh), not the unsigned
proxy. Follow [interpretation rules](interpretation.md) before attributing savings.
