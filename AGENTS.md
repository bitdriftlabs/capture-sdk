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
- When running all iOS tests, make sure to use `--build_test_only` as wildcard Bazel targets picks up build targets that don't build within the test context.

## Build System

Primary: **Bazel** (`./bazelw`). Secondary: Gradle for Android (`platform/jvm/gradlew`).

Key Bazel configs: `--config ios`, `--config android`, `--config release-ios`, `--config release-android`, `--config ci`

## Project Structure

```
platform/swift/source/   # iOS SDK (Swift + Rust FFI)
platform/jvm/capture/    # Android SDK (Kotlin)
platform/jvm/core/       # JNI bindings
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
- Rust: 1.90.0

## Key Dependencies

Depends on [shared-core](https://github.com/bitdriftlabs/shared-core) for `bd-logger`, `bd-buffer`, `bd-api`, `bd-crash-handler`, `bd-runtime`.

## Changelog

Update `CHANGELOG.md` under `### Both`, `### Android`, or `### iOS` with categories: **Added**, **Changed**, **Fixed**.
