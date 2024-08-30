# Capture SDK

The bitdrift Capture SDK is a highly optimized, lightweight library built to enable high volume, low overhead local telemetry storage and persistence. Controlled in real-time by the bitdrift control plane, the SDK selectively uploads the precise data needed to debug customer issues, and nothing more.

See [here](https://docs.bitdrift.io/product/overview) for more information.

## Building Requirements

### Dependencies

#### Rust

Install it locally using [their installation script](https://www.rust-lang.org/tools/install)

`curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`

make sure to add it to your path, e.g.

`echo 'export PATH="~/.cargo/bin:$PATH"' >> ~/.zshrc`

#### Other

Install other required dependencies using following commands:

```bash
brew install protobuf flatbuffers llvm
```

Make sure that `llvm-objcopy` is in your `PATH`.

## Development

The Capture SDK is built using [bazel](https://github.com/bazelbuild/bazel). The `./bazelw` ensures that the correct bazel version is used and the
correct Android dependencies are installed.

### Capture SDK Example Apps

The easiest way to test the library is by running the example apps on each platform (iOS / Android).

See [examples/README.md](/examples/README.md) for more details on how to setup your environment.

#### iOS

To run the iOS example app:

```bash
./bazelw run --ios_multi_cpus=x86_64 :ios_app
```

For more details on how to setup and run the example app using Xcode refer to [examples/README.md](/examples/README.md).

#### Android

We have two example apps on Android, one built with bazel and one built wih gradle so both build frameworks can be tested.

For more details on how to setup and run the example apps using Android Studio refer to [examples/README.md](/examples/README.md).

### Tests

To run all tests:

```bash
./bazelw test //... --build_tests_only
```

### Benchmarking

When making changes to the Rust logging path, the benchmarks in //test/benchmark:logger_benchmark
can be used to evaluate the impact of the change. To run, invoke

```bash
./bazelw build --config benchmark //test/benchmark:logger_benchmark
bazel-bin/test/benchmark/logger_benchmark --bench
```

Perform this for both the old and new change (in that order), then look at the relevant charts in
target/criterion/*/report for the different benchmark functions (the output from the benchmark
binary should indicate which ones are interesting).

### Dependency Management
We use crate_universe to manage our third party Rust dependencies. This tool inspects the dependencies
listed in Cargo.toml and uses it to generate a number of BUILD files that allow the Capture SDK code
to depend on these third party targets.

To depend on an imported library, depend on `@crate_index//:<name>`. For example, to depend on
tokio depend on `@crate_index//:tokio`.

### Rust Editor Support

To provide a IDE-like experience we make use of https://github.com/rust-lang/rust-analyzer and
editors with rust-analyzer support (e.g. VS Code with the `rust-analyzer` extension).

We rely on rust-analyzer's Cargo integration to provide IDE-like capabilities. One thing to note
is that we don't build the project, which means its possible for the Cargo configuration to diverge.
This will most likely be due to reference between crates within the project not being specified in the
relevant Cargo.toml files.

### Formatters

```bash
make format
```

Individual formatters can be run via specific make targets, see the
top-level Makefile.

Note that clippy checks are disabled by default in development due to
https://github.com/bazelbuild/rules_rust/issues/1372. To enable in dev, add `--config clippy` to your
bazel commands.

### Binary Size Comparison

As keeping the binary size down, it's sometimes helpful to check if a change increases the binary
size substantially. To get an estimate, run `./tools/capture_so_size.sh` which will compute the size of a
stripped .so compiled for Android.

### Building SDK Binaries

Run `./tools/ios_release.sh` and `./tools/android_release.sh` to build iOS and Android release artifacts
respectively.
