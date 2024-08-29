# Capture SDK

The bitdrift Capture SDK is a highly optimized, lightweight library built to enable high volume, low overhead local telemetry storage and persistence. Controlled in real-time by the bitdrift control plane, the SDK selectively uploads the precise data needed to debug customer issues, and nothing more.

See [here](https://docs.bitdrift.io/product/overview) for more information.

## Building Requirements

### Dependencies

Install required dependencies using following commands:

```bash
brew install protobuf flatbuffers llvm
```

Make sure that `llvm-objcopy` is in your `PATH`.

#### Xcode

Xcode 15.4 is used to compile Capture SDK on macOS. Download it from https://developer.apple.com/download/.

*If you install Xcode directly from the App store you will likely not get
the specific version above. Either install it manually or override the
version in your .bazelrc like this:*

```
build --xcode_version=15.2
```

Bazel can also get confused about the status of Xcode installation so if you run into issues with stale version confusion do:

```
./bazelw clean --expunge
./bazelw shutdown
```

If you are using a different version of Xcode/simulator you may also need to adjust the following settings in .bazelrc to match your environment:

```
build --ios_simulator_device="iPhone 15"
build --ios_simulator_version=17.5
```

## Development

The Capture SDK is built using [bazel](https://github.com/bazelbuild/bazel). The `./bazelw` ensures that the correct bazel version is used and the
correct Android dependencies are installed.

To run all tests:

```bash
./bazelw test //... --build_tests_only
```

### Debugging Capture SDK Hello World Apps

#### iOS

To run the iOS hello world app:

```bash
./bazelw run --ios_multi_cpus=x86_64 :ios_app
```

To create Xcode project iOS Capture SDK:

```bash
./bazelw run :xcodeproj
xed . // opens generated project
```

#### Android

To install the Android hello world app to an active arm64 emulator:

```bash
./bazelw mobile-install --fat_apk_cpu=arm64-v8a :android_app
```

See [examples/README.md](/examples/README.md) for more details for how to use IDE to develop locally on Android.

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
