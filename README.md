# Capture SDK

The bitdrift Capture SDK is a highly optimized, lightweight library built to enable high volume, low overhead local telemetry storage and persistence. Controlled in real-time by the bitdrift control plane, the SDK selectively uploads the precise data needed to debug customer issues, and nothing more.

See [here](https://docs.bitdrift.io/product/overview) for more information.

For setup, please refer to the [wiki](https://github.com/bitdriftlabs/capture-sdk/wiki)

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
