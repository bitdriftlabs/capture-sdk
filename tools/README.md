# Assortment of scripts and tools

## Crash Symbolication Tool

Symbolicate Android native crash stack traces using Bitdrift Capture SDK debug symbols.

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
./symbolicate.sh -d <dump_file> -v <version> [-a <arch>]
```

**Arguments:**
- `-d` - Crash dump file (required)
- `-v` - SDK version, e.g. `0.19.1` (required)
- `-a` - Architecture: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` (optional, auto-detected)

### Examples

```bash
# Auto-detect architecture
./symbolicate.sh -d dump.txt -v 0.19.1

# Specify architecture
./symbolicate.sh -d dump.txt -v 0.19.1 -a arm64-v8a
```

### Output

Displays the complete backtrace with symbolicated function names clearly marked:

```
========================================
SYMBOLICATED BACKTRACE
========================================
      #00 pc 000000000018e44c  /data/app/...base.apk (BuildId: 53299551ea24eea6)
       [SYMBOLICATED] alloc::vec::from_elem
      #01 pc 00000000000fbdac  /data/app/...base.apk (BuildId: 53299551ea24eea6)
       [SYMBOLICATED] <bincode::features::serde::de_borrowed::SerdeDecoder<DE> as serde_core::de::Deserializer>::deserialize_string
      #02 pc 00000000000faf80  /data/app/...base.apk (BuildId: 53299551ea24eea6)
       [SYMBOLICATED] bd_key_value::Store::get
      #03 pc 0000000000144fec  /apex/com.android.runtime/lib64/libart.so (BuildId: 76cae8c2fae7f4328bb0144fc1b9a546)
```

The script symbolicates any frame where the Build ID matches the provided symbols.

### How It Works

1. Validates prerequisites (`llvm-addr2line`, `rustfilt`, etc.)
2. Auto-detects architecture from crash dump (or uses provided `-a` flag)
3. Downloads symbols from `https://dl.bitdrift.io/sdk/android-maven/io/bitdrift/capture/{version}/`
4. Verifies Build IDs match between dump and symbols
5. Symbolicates all backtrace frames with matching Build IDs
6. Demangles Rust symbols using `rustfilt` (if available)
7. Auto-cleans temporary files on exit
