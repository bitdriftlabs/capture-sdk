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

### Discover iOS destinations

```bash
# List all available simulators and connected devices.
just capture-dev devices ios

# Open a fuzzy picker and print the chosen selector.
just capture-dev devices ios --select
```

The picker requires an interactive terminal; scripts and CI should use the stable `selector` identifiers from the regular or JSON listing. The listing never guesses a device: if Simulator services or physical-device discovery are unavailable, it prints a diagnostic and an actionable next step instead.

### Build, install, and launch the iOS sample

```bash
# Pick a simulator or a connected physical device, then build, install, and launch.
just capture-dev run ios

# Skip the picker with an explicit destination from `devices ios`.
just capture-dev run ios --simulator SIMULATOR-UDID
just capture-dev run ios --device DEVICE-UDID

# Headless mode requires an explicit destination, so CI never guesses.
just capture-dev run ios --non-interactive --simulator SIMULATOR-UDID
```

### Install iOS signing assets from Bitwarden

```bash
# Authenticate and unlock Bitwarden in the current shell.
export BW_SESSION="$(bw unlock --raw)"

# Download the assets only into a temporary directory, import the certificate into the login
# keychain, and install profiles in UserData.
just capture-dev setup ios --bitwarden-item SIGNING-ITEM-ID

# Suitable for a local script or CI, with the item identifier supplied as a secret.
CAPTURE_DEV_IOS_SIGNING_ITEM=SIGNING-ITEM-ID just capture-dev setup ios
```

`capture-dev` never prints the Bitwarden session, the certificate password, or the downloaded asset contents. A locked vault or an incomplete item produces an actionable error without making any keychain or profile changes.

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
