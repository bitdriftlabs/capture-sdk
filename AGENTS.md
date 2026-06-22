# AGENTS.md

Multi-platform telemetry SDK for iOS/Android with a shared Rust core.

## Quick Reference

```bash
# Format (run before commits)
make format

# Tests
make test-gradle                    # Android unit tests
cargo nextest run -p swift_bridge   # Rust bridge tests
./bazelw test //test/platform/swift/unit_integration/core:test --config ios  # iOS tests
./bazelw test //platform/jvm/...    # Android tests via Bazel

# Build example apps
make run-ios-app-direct             # iOS (fastest)
./bazelw build --android_platforms=@rules_android//:x86_64 //examples/android:android_app

# Other
make repin                          # Fix "Digests do not match" errors
make xcframework                    # Build iOS release artifact
```

## Testing

- iOS tests must be run through Bazel.
- Android is often easier to run through Gradle but sometimes issues only reproduce through Bazel. platform/jvm/gradlew can be invoked directly (use -p to target the correct directory).
- When running all iOS tests, make sure to use `--build_tests_only` as wildcard Bazel targets picks up build targets that don't build within the test context.

### Cross-Repo Changes

When a change touches both `shared-core` and `capture-sdk`, use this workflow:

1. Make the `shared-core` changes on a branch and push that branch upstream first.
2. In `capture-sdk`, update the `shared-core` git `rev` values in `Cargo.toml`.
3. Treat `Cargo.toml` as the manual source of truth for the `shared-core` refs. The first Bazel run after the `rev` bump refreshes the derived cargo-bazel state automatically.
4. The first Bazel test command after updating the `shared-core` refs must use `CARGO_BAZEL_REPIN=true`.

Recommended pre-repin checks for the touched `capture-sdk` slices:

- Run the narrow Rust/unit checks that cover the edited code before the full Bazel pass.
- For shared metadata or bridge wiring, prefer `cargo nextest run -p platform-shared`, targeted Android unit tests from `platform/jvm`, and the narrow iOS Bazel target `//test/platform/swift/unit_integration/core:test`.
- If the `shared-core` bump changes a Rust API, expect the first breakage to show up in `platform/jvm/core` or other bridge/wiring crates that call into shared-core directly.

Standard full verification:

```bash
CARGO_BAZEL_REPIN=true ./bazelw test //... --build_tests_only --config ios --ios_simulator_device="iPhone 17"
```

After that first repin run, subsequent Bazel reruns can drop `CARGO_BAZEL_REPIN=true`.

When the repin command fails, use this recovery flow instead of switching between unrelated commands:

1. Fix the first concrete compile, test, or lint failure reported by Bazel.
2. If Bazel reports `ktlint` failures, read the referenced `test.log` files to get the exact file and line numbers before editing.
3. Rerun the same full Bazel command until it is green.
4. Only after the canonical repin command passes should you move on to broader follow-up or branch cleanup.

## Build System

Primary: **Bazel** (`./bazelw`). Secondary: Gradle for Android (`platform/jvm/gradlew`).

Key Bazel configs: `--config ios`, `--config android`, `--config release-ios`, `--config release-android`, `--config ci`

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

## Key Dependencies

Depends on [shared-core](https://github.com/bitdriftlabs/shared-core) for `bd-logger`, `bd-buffer`, `bd-api`, `bd-crash-handler`, `bd-runtime`.

## Changelog

Update `CHANGELOG.md` under `### Both`, `### Android`, or `### iOS` with categories: **Added**, **Changed**, **Fixed**.
