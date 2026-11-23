# Capture SDK

The bitdrift Capture SDK is a highly optimized, lightweight library built to enable high volume, low overhead local telemetry storage and persistence. Controlled in real-time by the bitdrift control plane, the SDK selectively uploads the precise data needed to debug customer issues, and nothing more.

See [here](https://docs.bitdrift.io/product/overview) for more information.

For setup, please refer to the [wiki](https://github.com/bitdriftlabs/capture-sdk/wiki)

## Documentation

- [Android crash profiling](docs/android-crash-profiling.md): automatic collection, caller-managed
  imports, file ownership, and pending profile storage.
- [Gradle test app: manual crash-profile testing](platform/jvm/gradle-test-app/docs/crash-profiling.md):
  emulator/device setup, ANR/OOM scenarios, symbol uploads, backend verification, and cleanup.
- [React Native development and releases](platform/es/README.md)

## Running tests

Use `./bazelw test //...` for the default tests supported on your machine. To work on one
part of the SDK, run the owning target directly.

| Coverage | Command |
| --- | --- |
| iOS XCTest suite | `./bazelw test //test/platform/swift/unit_integration/...` |
| Swift bridge Rust tests, including commands | `./bazelw test //platform/swift/source:swift_bridge_test` |
| Android Bazel tests | `./bazelw test //platform/jvm/...` |
| Xcode 27 and iOS 27 checks | `./bazelw test --config=ios27-tests //test/platform/swift/unit_integration/core:ios27_test` |

Xcode 27 and the iOS 27 simulator are required only for the opt-in check. BuildBuddy enables
it in **Test** and routes Apple targets to the Mac executor. **Clippy** runs separately.
Gradle plugin, Timber, and emulator instrumentation checks run in GitHub Actions when their
inputs change. The artifact workflows validate the packaged apps and report sizes.

## Local formatting

`make format` uses Bazel-managed Kotlin and Rust formatters plus local `buildifier`, `taplo`,
`swiftlint`, and `shellcheck`. On macOS, install the local tools with:

```sh
brew install buildifier taplo swiftlint shellcheck
```

For Swift documentation, use `make fix-docstrings FILE=path/to/File.swift` to format one file.
`make lint-docstrings` runs the same read-only check as `make format` and CI.

## Release build cache policy

Publishing workflows rebuild release artifacts with `--config=nocache`. Any GitHub Actions
dependency caches they use are scoped to `release-${{ github.workflow }}`.

The BuildBuddy **iOS CI artifacts** and **Android CI artifacts** workflows use optimized builds
for smoke tests and size reports on pull requests and `main`. They can reuse the ordinary CI
cache. See `AGENTS.md` for the cache requirements when changing either path.

## Release version policy

The SDK SemVer minor must equal the included shared-core `CONFIGURATION_VERSION`. For config
version 49, releases such as `0.49.0`, `0.49.1`, and `0.49.0-rc.1` are valid; `0.26.0` is not.
Patch and prerelease versions do not change the required config version. Major releases must
also retain the matching minor rather than reset it to zero.

Release CI builds a host binary against the resolved shared-core dependency and compares its
output with the requested minor before building release artifacts. Publication and downstream
release dispatches validate the existing native release tag. The check applies to dry runs and
has no emergency bypass. To check a version locally:

```sh
config_version="$(./bazelw run --config=nocache //tools/config_version)"
python3 ci/check_release_config_version.py --version 0.49.0 --config-version "$config_version"
```

## Rust Analyzer

This section uses [just](https://github.com/casey/just), a modern Make alternative. Install via `brew install just`.

Generate the Bazel-derived Rust crate graph after cloning and whenever Bazel or Rust dependency
configuration changes:

```sh
just ra
```

This installs the Bazel-pinned Rust Analyzer tools under ignored local state, generates a root
`rust-project.json`, and configures VS Code to use them. The generated project includes test
targets and their development dependencies.

To limit indexing to local areas and their transitive Rust dependencies, select one or more paths.
Each invocation replaces the previous local selection:

```sh
just select-ra platform/jvm/core
```

The command writes the ignored `.rules_rust_analyzer/selection.json` and runs the same setup as
`just ra`. To edit that selection directly, use this format:

```json
{
  "paths": [
    "platform/shared",
    "platform/crash"
  ]
}
```

To restore the full project, remove the ignored selection and run `just ra`:

```sh
rm .rules_rust_analyzer/selection.json
just ra
```
