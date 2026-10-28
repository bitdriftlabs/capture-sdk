# capture-dev

`capture-dev` is the local developer tool for Capture SDK. It is intended to make building, running, testing, and (eventually) integration testing discoverable from one command-line interface.

## Run it

Run commands from the `capture-sdk` repository root. `just` is the friendly entry point:

```bash
just capture-dev
```

With no arguments, this runs `doctor` for every supported platform.

### Doctor

```bash
# Check iOS tooling, local signing assets, and connected Apple devices.
just capture-dev doctor ios

# Require a currently usable physical device. Missing or inaccessible devices
# are reported as blocking.
just capture-dev doctor ios --for-device-install

# Machine-readable output for CI or another tool.
just capture-dev doctor ios --for-device-install --json

# Show the current Android doctor placeholder.
just capture-dev doctor android

# Discover available commands and flags.
just capture-dev --help
```

## Build and test

```bash
# Build with Cargo:
cargo build --package capture-dev

# or build using bazel:
./bazelw build //tools/capture-dev

# Run tests.
cargo nextest run --package capture-dev

# Lint and format-check the tool.
cargo clippy --package capture-dev --bins --tests -- --no-deps
cargo +nightly fmt --check --package capture-dev
```

To run the Cargo-built binary directly instead of through `just`:

```bash
cargo run --quiet --package capture-dev -- doctor ios
```
