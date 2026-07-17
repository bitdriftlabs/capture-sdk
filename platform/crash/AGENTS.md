# AGENTS.md

Local guidance for `platform/crash` (`bd-crash-reporter`).

## Base Rules

- Primary instructions should be sourced from rust.instructions.md
- ALWAYS use `cargo nextest` commands for verification as it's faster

## What This Crate Does

`bd-crash-reporter` persists a small `mmap`-backed crash record so the next launch can inspect
whether the previous run terminated while crash monitoring was active.

## Core Invariants

- `CRASH_RECORD` must only point at a live `mmap` owned by the configured coordinator.
- `Committed` must not become visible before the payload bytes are fully written.
- Repeated configure calls must be idempotent and must not construct/drop a second coordinator
  while the shared record pointer still references the original mapping.
- Persisted bytes are untrusted on the next launch: malformed strings, unexpected versions, and
  incomplete records must be ignored instead of treated as valid state.

## Editing Guidance

- Prefer small helpers for lifecycle transitions such as record publication and monitor install /
  uninstall paths.
- Keep safety comments next to unsafe pointer casts, volatile writes, and persisted-record parsing.
- Test-only helpers shared by multiple `_test.rs` files should live in `src/test_support.rs`, not in
  production modules.
- If a change touches the persisted schema, treat backward-compatible parsing as mandatory work.

## Local Verification

Run these first when touching only this crate:

```bash
cargo nextest run -p bd-crash-reporter
cargo clippy -p bd-crash-reporter --tests --quiet
cargo fmt --manifest-path platform/crash/Cargo.toml
```

Useful Bazel targets for this folder:

```bash
./bazelw test //platform/crash:bd_crash_reporter_test
./bazelw test //platform/crash:_bd_crash_reporter_rust_clippy
```

## Cross-Platform Smoke Checks

 When the change is broader than crate-local Rust logic, run smoke checks at the SDK level too.
 Prefer canonical `make` entrypoints when the repo provides them;
 otherwise keep the exact `bazel` target in the doc.

iOS:

```bash
./bazelw test //test/platform/swift/unit_integration/core:test --config ios
make xcframework
```

Android:

```bash
make test-gradle
./bazelw build :capture_aar --config ci --config release-android \
  --android_platforms=@rules_android//:x86_64
```
