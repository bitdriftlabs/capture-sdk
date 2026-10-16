# Assortment of scripts and tools

## iOS local-universe log flow

Build the **source-linked Simulator** sample (not `:ios_app`, which uses the distribution XCFramework):

```sh
./bazelw build --ios_multi_cpus=sim_arm64 //examples/swift/hello_world:hello_world_app
./bazelw cquery --ios_multi_cpus=sim_arm64 --output=files //examples/swift/hello_world:hello_world_app
./bazelw info execution_root
```

Resolve the cquery output relative to `execution_root`, not `bazel-bin`. This build reports
`hello_world_app.ipa`, but it is a **Simulator archive**, containing
`Payload/Bitdrift Sample App.app` with `CFBundleSupportedPlatforms = [iPhoneSimulator]`.
Extract that bundle for `simctl install`; do not use a device IPA or guess a configuration-specific
Bazel directory. The bundle ID is `io.bitdrift.example.swiftapp.helloworld`.

Before SDK initialization, the Simulator sample reads these **process environment** variables:

- `BITDRIFT_E2E=1`
- `BITDRIFT_E2E_API_URL=http://127.0.0.1:8001` (HTTP/HTTPS loopback origin only;
  `localhost` and `[::1]` are also accepted)
- `BITDRIFT_E2E_API_KEY` (nonempty; supply only through the launch environment, never Maestro)
- `BITDRIFT_E2E_RUN` (nonempty unique run marker, attached as the initial/global field `workflow_e2e_run`)

With `simctl launch`, prefix each variable with `SIMCTL_CHILD_`. The launcher must install and launch
the app with all four variables **before** running the flow. Overrides are never written to UserDefaults.
Any partial or invalid E2E configuration prevents SDK startup and displays a red error in the existing
SDK State panel (`e2e_startup_error`); it never falls back to saved or production credentials.
When none of these variables are present, ordinary saved configuration and the production default
are unchanged. Physical-device builds ignore these overrides.

With the app already running on the Debug Panel:

```sh
maestro --device "$SIMULATOR_UDID" test \
  -e MESSAGE="local-universe-negative-$RUN_ID" \
  tools/maestro/ios-local-universe-log.yaml
```

The flow foregrounds without stopping, relaunching, clearing app state, or changing credentials. It
clears the composer through its explicit Clear message control (not caret-relative backspaces),
uses Done editing to dismiss the keyboard, and asserts the field exactly equals `MESSAGE`.
After selecting INFO and scrolling to Send, it asserts the text again immediately before sending
exactly one supplied message, so scrolling cannot silently change the input. This final check reads
the visible preview bound to the same message state submitted to the logger; iOS omits the offscreen
input field from its accessibility tree.
It returns to the Debug Panel, so the same flow can run again with a distinct positive-phase message after the caller
independently verifies the negative phase and changes/verifies backend configuration.
`APP_ID` may optionally override the default bundle ID. Keep messages nonempty and ASCII
(Maestro `inputText` limitation), with no surrounding whitespace (the sample trims it before logging).
Do not pass the API key to Maestro or print it.

Stable UI IDs: `manual_log_open`, `log_message`, `log_message_clear`, `log_message_done`,
`log_message_preview`,
`log_level_info` (also the other level raw values),
`log_send`, `session_id`, `sdk_check_state`, `sdk_initialization_state`, `sdk_last_handshake`,
`sdk_last_config_delivery`, and `e2e_startup_error`. The existing SDK State timestamps are readiness
hints, not backend identity or proof that a particular configuration was applied. The caller owns
backend identity checks, configuration checks, and negative/positive log queries; there is no flush hook.

Focused startup-validation tests:

```sh
./bazelw test --ios_multi_cpus=sim_arm64 --ios_simulator_version=26.0 \
  //examples/swift/hello_world/tests:test
```

Select an installed Simulator runtime if iOS 26.0 is unavailable.

## Crash Symbolication Tool

Symbolicate Android native crash stack traces using Bitdrift Capture SDK debug symbols. Supports standard Android Logcat/Tombstone traces, custom crash reporting dumps like from bitdrift Capture, Firebase Crashlytics absolute-address format, Bugsnag, and raw `debuggerd` output formats.

### Prerequisites

```bash
brew install llvm
```

#### Rust Symbol Demangling (Optional)
```bash
cargo install rustfilt
```

### Usage

```bash
./tools/symbolicate_bd_frames.sh -d <dump_file> -v <version> [-a <arch>] [-o <output_file>]
```

**Arguments:**
- `-d` - Crash dump file (required)
- `-v` - SDK version, e.g. `0.19.1` (required)
- `-a` - Architecture: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` (optional, auto-detected)
- `-o` - Output file to save the symbolicated trace (optional)

### Examples

```bash
# Auto-detect architecture
./tools/symbolicate_bd_frames.sh -d dump.txt -v 0.19.1

# Specify architecture
./tools/symbolicate_bd_frames.sh -d dump.txt -v 0.19.1 -a arm64-v8a

# Save output to file
./tools/symbolicate_bd_frames.sh -d dump.txt -v 0.19.1 -o symbolicated.txt
```

### Output

Displays the complete backtrace with symbolicated function names replacing the binary path and offset, clearly marked with a `[symbolicated]` tag:

**Logcat / Tombstone format:**
```
2026-03-06 10:12:22.304 18860 A #00 pc 000000000018e44c [symbolicated] alloc::vec::from_elem
2026-03-06 10:12:22.304 18860 A #01 pc 00000000000fbdac [symbolicated] <bincode::features::serde::de_borrowed::SerdeDecoder<DE> as serde_core::de::Deserializer>::deserialize_string
2026-03-06 10:12:22.304 18860 A #02 pc 00000000000faf80 [symbolicated] bd_key_value::Store::get
2026-03-06 10:12:22.304 18860 A #03 pc 0000000000144fec  /apex/com.android.runtime/lib64/libart.so (BuildId: 76cae8c2fae7f4328bb0144fc1b9a546)
```

**Raw debuggerd format:**
```
#00 0x000000000018e44c [symbolicated] alloc::vec::from_elem
#01 0x00000000000fbdac [symbolicated] <bincode::features::serde::de_borrowed::SerdeDecoder<DE> as serde_core::de::Deserializer>::deserialize_string
#02 0x00000000000faf80 [symbolicated] bd_key_value::Store::get
```

**Bugsnag format:**
```
STACKTRACE
  #00 pc 000000000021b334 [symbolicated] bd_workflows::engine::WorkflowsEngine::process_event
  #01 pc 000000000020a1bc [symbolicated] bd_workflows::engine::WorkflowsEngine::new
```

**Absolute address format (bitdrift / Firebase Crashlytics):**
```
   <unknown> at 0x77d4f399ac [symbolicated] bd_key_value::Store::get
   <unknown> at 0x77d4edaab4 [symbolicated] tokio::sync::oneshot::Sender<T>::send
   <unknown> at 0x77d4edbd6c [symbolicated] <bd_buffer::buffer::volatile_ring_buffer::ProducerImpl as bd_buffer::buffer::RingBufferProducer>::reserve
```

The script symbolicates any frame where the Build ID matches the provided symbols. For dumps with absolute virtual addresses (e.g. `at 0x<addr> within <path>`), the script automatically estimates the library base address from the minimum observed address in the native APK.

### How It Works

1. Validates prerequisites (`llvm-addr2line`, `rustfilt`, etc.)
2. Auto-detects architecture from crash dump (or uses provided `-a` flag)
3. Downloads symbols from `https://dl.bitdrift.io/sdk/android-maven/io/bitdrift/capture/{version}/`
4. Verifies Build IDs match between dump and symbols (or selectively ignores it for supported edge cases like Bugsnag's `base.apk` frames)
5. For absolute-address formats, estimates the library base address from the native APK's minimum observed address (page-aligned) and converts to relative offsets
6. Symbolicates all backtrace frames matching our module or Build ID
7. Demangles Rust symbols using `rustfilt` (if available)
8. Auto-cleans temporary files on exit
