# AGENTS.md

Multi-platform telemetry SDK for iOS/Android with a shared Rust core.

## Quick Reference

```bash
# Format (run before commits or handing off changes)
make format
make lint-docstrings                # Required after Swift edits if not running make format

# Tests
make test-gradle                    # Android unit tests
./bazelw test //platform/swift/source:swift_bridge_test  # Rust bridge tests
./bazelw test //test/platform/swift/unit_integration/core:test  # iOS tests
./bazelw test //platform/jvm/...    # Android tests via Bazel

# Build example apps
make run-ios-app-direct             # iOS (fastest)
./bazelw build --android_platforms=@rules_android//:x86_64 //examples/android:android_app

# Other
make repin                          # Refresh Cargo, Bazel, and Maven lockfiles
make xcframework                    # Build iOS release artifact
```

## Formatting at closeout

- Run the repository's `make format` before handing off changes, then review the resulting diff.
- If using focused validation for a Swift change, run `make lint-docstrings` after the final edit.
  SwiftLint and XCTest do not run this CI check. Fix alignment with
  `make fix-docstrings FILE=path/to/File.swift`, then rerun `make lint-docstrings` and report the
  command you actually ran.

## Testing

- iOS tests must be run through Bazel.
- See [Running tests](README.md#running-tests) for the default suite and the Xcode 27 opt-in.
- Android is often easier to run through Gradle but sometimes issues only reproduce through Bazel. platform/jvm/gradlew can be invoked directly (use -p to target the correct directory).
- `test --build_tests_only` is configured in `.bazelrc`, so wildcard test commands build only test targets rather than unrelated build targets.

## Clippy

BuildBuddy Test enables `--config=clippy-in-compile` to compile and lint first-party Rust libraries
and tests together. CI Clippy test targets keep production libraries and lint-only checks in the
graph with `--build_tests_only`. The mixed RBE configuration routes Apple checks to macOS.

For standalone local linting on macOS, use:

```bash
./ci/run_clippy.sh all full /dev/null
```

For a changed Rust library, run its direct Clippy build target before broader validation. For
example, JNI core changes require:

```bash
./bazelw build --config=clippy //platform/jvm/core:capture_core
```

After any Clippy-driven Rust edit, run `make format`, then rerun the focused Clippy command. Use
`ci/run_clippy.sh` for a broader standalone lint check when practical.

### Running One iOS XCTest With Rust Logs

Use `--test_filter` with the XCTest `ClassName/testMethod` identifier. Pass `RUST_LOG` with
`--test_env`; Bazel forwards it to the simulator test process.

```bash
./bazelw test //test/platform/swift/unit_integration/core:test \
	--test_filter='CaptureNetworkTests/testHappyPathWithTimeoutAndReconnect' \
	--test_env=RUST_LOG='info,bd_api=debug,bd_test_helpers=debug' \
	--test_output=streamed \
	--build_tests_only \
	--ios_simulator_device="iPhone 17"
```

### Cross-Repo Changes

When a change touches both `shared-core` and `capture-sdk`, use this workflow:

1. Make the `shared-core` changes on a branch and push that branch upstream first.
2. In `capture-sdk`, update the `shared-core` git `rev` values in `Cargo.toml`.
3. Run `make repin`. It runs `cargo update` first because `rules_rs` reads `Cargo.lock`
   rather than regenerating it. Then it refreshes `MODULE.bazel.lock` and the Maven pins.
4. Review and commit the manifest and regenerated lockfiles together.

Recommended pre-repin checks for the touched `capture-sdk` slices:

- Run the narrow Rust/unit checks that cover the edited code before the full Bazel pass.
- For shared metadata or bridge wiring, prefer `cargo nextest run -p platform-shared`, targeted Android unit tests from `platform/jvm`, and the narrow iOS Bazel target `//test/platform/swift/unit_integration/core:test`.
- If the `shared-core` bump changes a Rust API, expect the first breakage to show up in `platform/jvm/core` or other bridge/wiring crates that call into shared-core directly.

Standard full verification:

```bash
./bazelw test //... --ios_simulator_device="iPhone 17"
```

If `make repin` fails, fix the first Cargo resolution or Bazel pinning error and rerun it
before building or testing.

## Build System

Primary: **Bazel** (`./bazelw`). Secondary: Gradle for Android (`platform/jvm/gradlew`).

Key Bazel configs: `--config android`, `--config release-ios`, `--config release-android`, `--config ci`

Maven dependencies are pinned in `maven_install.json`. After changing a `maven.install` or
`maven.artifact` entry in `MODULE.bazel`, run `make repin` and commit the regenerated lock file.

## SDK Size Comparisons

Load [sdk-size-analysis](.claude/skills/sdk-size-analysis/SKILL.md) for Android/iOS size work.
Its one-command entry point owns setup, release builds, artifact discovery, accounting, audits,
recovery and publication; its references are the canonical measurement and interpretation docs.
Use this standalone SDK's `./bazelw`, preserve user/dependency edits, and keep scratch under `.tmp/`.
Do not equate framework metadata or an unsigned stripped-app proxy with signed App Thinning.

## Release Build Cache Policy

Build publishable SDK and example-app artifacts in the release workflows with `--config=nocache`
and set `enable-bazel-cache: "false"` on their shared setup actions. Scope any GitHub Actions
dependency caches, including Gradle caches, to `release-${{ github.workflow }}`.

The BuildBuddy **iOS CI artifacts** and **Android CI artifacts** workflows may reuse normal CI
action results for pull requests and `main`. Their optimized artifacts are for smoke tests
and size reports only. Signed sample apps from App Thinning checks remain CI artifacts.

## Project Structure

```
platform/swift/source/   # iOS SDK (Swift + Rust FFI)
platform/jvm/capture/    # Android SDK (Kotlin)
platform/jvm/core/       # JNI bindings
platform/crash/          # Crash reporter Rust crate; see local AGENTS.md
core/                    # Core Rust libraries
test/                    # Integration tests
```

Test locations:
- iOS: `test/platform/swift/unit_integration/`
- Android: `platform/jvm/capture/src/test/`, `platform/jvm/gradle-test-app/`
- Rust bridge: `test/platform/swift/bridging/`

## Platform Requirements

- iOS: Xcode 16.2, minimum iOS 15.0
- Android: JDK 17, NDK 27.2, minSdk 23
- Rust: 1.95.0

## FFI and ABI Safety

Changes at the Swift C bridge or Android JNI boundary are release-critical. Treat a function
signature as an ABI contract: its parameter count, order, nullability, and pointer types must
match exactly across every declaration, generated binding, and Rust export. In particular, never
add an unused trailing parameter to a Rust C/JNI export, and use Objective-C object types (such as
nullable `NSString *`) rather than untyped pointers when Swift passes Foundation values.

When changing an FFI entry point, inspect and update every layer together:

- the Rust `extern "C"` or JNI export;
- `CaptureRustBridge.h` and every Swift call site, or the Kotlin `external` declaration and callers;
- compatibility overloads exposed to Swift, Objective-C, Kotlin, or Java.

Run focused bridge compilation/tests for every FFI change. Do not rely on Rust-only compilation to
validate Swift header imports or JNI call signatures.

## Key Dependencies

Depends on [shared-core](https://github.com/bitdriftlabs/shared-core) for `bd-logger`, `bd-buffer`, `bd-api`, `bd-crash-handler`, `bd-runtime`.

## Changelog

Update `CHANGELOG.md` for every user-facing behavior change under `### Both`, `### Android`,
or `### iOS` with categories: **Added**, **Changed**, **Fixed**. Do not add an entry for CI,
build, tooling, test-only, documentation-only, or internal changes that do not affect SDK behavior.
