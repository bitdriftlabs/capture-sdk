# AGENTS.md

This file provides guidance to Agents when working with code in this repository.

## Overview

The bitdrift Capture SDK is a multi-platform telemetry library for iOS and Android. It uses a shared Rust core with platform-specific bindings. The SDK enables high-volume, low-overhead local telemetry storage controlled by a remote control plane.

## Build System

The primary build system is **Bazel** via `./bazelw`. Gradle is used secondarily for Android-specific tasks.

### Common Commands

```bash
# Format all code
make format

# Individual formatters
make ktlint          # Kotlin
make rustfmt         # Rust
make buildifier      # Bazel BUILD files
make fix-swift       # Swift (swiftlint + docstring validation)
make lint-yaml       # TOML/YAML via taplo

# Build iOS XCFramework
make xcframework     # Output: bazel-bin/Capture.ios.zip

# Repin Cargo dependencies (when you get "Digests do not match" errors)
make repin

# Generate Kotlin from FlatBuffers schemas
make gen-flatbuffers
```

### Running Tests

**iOS:**
```bash
# Swift bridge unit tests (prefer nextest for Rust tests)
cargo nextest run --package swift_bridge

# iOS integration tests (requires macOS)
./bazelw test $(./bazelw query 'kind(ios_unit_test, //test/platform/swift/unit_integration/...)') --test_tag_filters=macos_only --config ci --config ios
```

**Android:**
```bash
# Kotlin unit tests (from platform/jvm/)
./gradlew capture:testDebugUnitTest --info
./gradlew capture-timber:testDebugUnitTest --info

# Build microbenchmarks
./gradlew microbenchmark:assembleAndroidTest

# Instrumentation tests (requires emulator)
./gradlew gradle-test-app:connectedCheck
```

**Rust/Linux:**
```bash
./bazelw test //core/... //platform/... //test/... --config ci --config nomacos --config libunwind --test_output=errors
```

### Building Apps

```bash
# iOS example app
./bazelw build --config release-ios //examples/swift/hello_world:ios_app

# Android example app (x86_64 for emulator)
./bazelw build --config release-android --android_platforms=@rules_android//:x86_64 //examples/android:android_app

# Android AAR
./bazelw build :capture_aar --config release-android
```

## Architecture

```
capture-sdk/
├── platform/
│   ├── swift/source/     # iOS SDK (Swift + Rust FFI bridge)
│   ├── jvm/              # Android SDK
│   │   ├── capture/      # Main Android library
│   │   ├── capture-apollo/   # Apollo GraphQL integration
│   │   ├── capture-timber/   # Timber logging integration
│   │   ├── replay/       # Session replay module
│   │   ├── capture-plugin/   # Gradle plugin for OkHttp instrumentation
│   │   ├── common/       # Shared Android utilities
│   │   └── core/         # JNI bindings to Rust
│   ├── shared/           # Shared Rust code (error handling, metadata, JS errors)
│   └── test_helpers/     # Shared test utilities
├── core/                 # Core Rust libraries
├── proto/                # Protocol buffers and FlatBuffers schemas
├── examples/
│   ├── swift/            # iOS example apps
│   └── android/          # Android example app
└── test/                 # Integration tests and benchmarks
```

### Key Dependencies

The SDK depends heavily on `shared-core` (github.com/bitdriftlabs/shared-core) for:
- `bd-logger` - Core logging infrastructure
- `bd-buffer` - Ring buffer implementation
- `bd-api` - API client
- `bd-crash-handler` - Crash handling
- `bd-runtime` - Runtime configuration

### Platform Requirements

- **iOS**: Minimum iOS 15.0, Xcode 16.2
- **Android**: minSdk 23, targetSdk 36, NDK 27.2
- **Rust**: 1.90.0

## Bazel Configurations

Key configs in `.bazelrc`:
- `--config ios` / `--config android` - Platform selection
- `--config release-ios` / `--config release-android` - Release builds with optimizations
- `--config ci` - CI mode with clippy enabled
- `--config nomacos` - Skip macOS-only targets (for Linux builds)

## Changelog

When making changes, update `CHANGELOG.md` under the appropriate section:
- `### Both` - Changes affecting both platforms
- `### Android` - Android-specific changes
- `### iOS` - iOS-specific changes

Use categories: **Added**, **Changed**, **Fixed**
