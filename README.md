# Capture SDK

The bitdrift Capture SDK is a highly optimized, lightweight library built to enable high volume, low overhead local telemetry storage and persistence. Controlled in real-time by the bitdrift control plane, the SDK selectively uploads the precise data needed to debug customer issues, and nothing more.

See [here](https://docs.bitdrift.io/product/overview) for more information.

For setup, please refer to the [wiki](https://github.com/bitdriftlabs/capture-sdk/wiki)

## Licensing

First-party code is licensed under [PolyForm Strict License 1.0.0](LICENSE), a source-available
license, not an open-source license. Previously published versions retain their original
licenses. Third-party components retain their own licenses and notices.

The root `LICENSE` is the canonical text for source and release artifacts. Check license
metadata and headers with `ci/check_license.sh`; add missing headers or normalize canonical
header formatting with `python3 ci/license_header.py`. Noncanonical first-party headers are
rejected, not migrated.

## Local formatting

`make format` uses Bazel-managed Kotlin and Rust formatters plus local `buildifier`, `taplo`,
`swiftlint`, and `shellcheck`. On macOS, install the local tools with:

```sh
brew install buildifier taplo swiftlint shellcheck
```

## Release build cache policy

Release builds do not use the Bazel/BuildBuddy remote cache. GitHub Actions caches used by a
release are scoped as `release-${{ github.workflow }}` and must not be shared with pull-request or
ordinary `main` builds. See `AGENTS.md` before changing release commands, workflows, or shared
setup actions.

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
