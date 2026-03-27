# Assortment of scripts and tools

## Crash Symbolication Tool

Symbolicate Android native crash stack traces using Bitdrift Capture SDK debug symbols. Supports standard Android Logcat/Tombstone traces, custom crash reporting dumps like from Bugsnag, and raw `debuggerd` output formats.

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

The script symbolicates any frame where the Build ID matches the provided symbols.

### How It Works

1. Validates prerequisites (`llvm-addr2line`, `rustfilt`, etc.)
2. Auto-detects architecture from crash dump (or uses provided `-a` flag)
3. Downloads symbols from `https://dl.bitdrift.io/sdk/android-maven/io/bitdrift/capture/{version}/`
4. Verifies Build IDs match between dump and symbols (or selectively ignores it for supported edge cases like Bugsnag's `base.apk` frames)
5. Symbolicates all backtrace frames matching our module or Build ID
6. Demangles Rust symbols using `rustfilt` (if available)
7. Auto-cleans temporary files on exit
