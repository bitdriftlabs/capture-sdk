# Capture SDK

The bitdrift Capture SDK is a highly optimized, lightweight library built to enable high volume, low overhead local telemetry storage and persistence. Controlled in real-time by the bitdrift control plane, the SDK selectively uploads the precise data needed to debug customer issues, and nothing more.

See [here](https://docs.bitdrift.io/product/overview) for more information.

For setup, please refer to the [wiki](https://github.com/bitdriftlabs/capture-sdk/wiki)

## Development

### Rust formatting

Capture SDK is a standalone Bazel workspace, including when it is checked out as a monorepo
submodule. Run `make rustfmt` from this directory for Capture SDK Rust changes. Do not use the
monorepo's `just rustfmt`: it resolves Rustfmt through the monorepo Bazel execroot, which does not
match Capture SDK's standalone Bazel workspace.
