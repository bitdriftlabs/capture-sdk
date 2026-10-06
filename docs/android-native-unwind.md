# Android Native Unwind Metadata

Android release libraries retain compiler-generated unwind rules. ARM64, x86,
and x86_64 package those rules in XZ-compressed GNU MiniDebugInfo rather than
runtime-mapped unwind tables. ARMv7 retains its ARM exception-index tables.

## Build Pipeline

The linker script in `bazel/android/cfi.ld` marks `.eh_frame` non-allocated before
the final ELF layout is chosen. It preserves the original CIE/FDE records and
relocations; no conversion to `.debug_frame` or post-link segment rewriting is
needed. The linker does not generate `.eh_frame_hdr`.

`android_debug_info` uses the NDK's `llvm-objcopy` and `llvm-strip`, plus Bazel's
pinned xz executable, to extract the CFI into a small ELF, compress it, and add it
as `.gnu_debugdata` to the stripped library. Packaging fails if extraction produces
no CFI bytes. Intermediate ELF and XZ files are removed after embedding. Full debug
symbols remain a separate output. Rust, native libraries, and Android stdlib
artifacts must continue generating unwind metadata.

Build the release AAR and symbols:

```sh
./bazelw build --config=release-android --config=nocache \
  //:capture_aar //:capture_symbols //:capture.debug_info
```

For collectors that require conventional runtime-mapped tables, disable both the
linker-script selection and compression with:

```sh
./bazelw build --config=release-android --config=nocache \
  --define android_compress_cfi=false //:capture_aar
```

Gradle's ordinary release variant uses the same packaged Bazel output. Its
profileable variant bypasses final stripping and retains conventional unwind
tables; debug builds are unchanged.

## Compatibility

Android 12's system crash unwinder reads `.eh_frame` inside `.gnu_debugdata`.
ARM64 API 31 emulator tests reproduced identical native PCs for the fixture and
the Bazel-packaged SDK with compressed versus uncompressed CFI, both with native
libraries extracted and loaded directly from the APK. Removing CFI truncated both
traces. The SDK probe intentionally faults a JNI call; it does not
validate normal SDK operation. x86 and x86_64 received build and ELF checks, not
device tests. Android versions older than 12 were not verified.

All four ABI artifacts retain 16 KiB ELF load-segment alignment. Runtime checks
used a 4 KiB-page emulator, not a 16 KiB-page device.

This does not provide a universal replacement for runtime unwind tables. Stack
collectors must understand GNU MiniDebugInfo and access the backing ELF or APK.
Deleted or inaccessible library files, memory-only collectors, exception
unwinders, and ordinary `_Unwind_Backtrace` consumers may have shorter traces.
ANR collectors, profilers, and third-party crash tools were not verified. Runtime
decompression latency and memory overhead were not measured.

## Size Results

Fresh builds on 2026-10-06 compared the same SDK branch with compression enabled
and disabled. These are not the older protobuf-spike measurements.

| Artifact | Conventional CFI bytes | Compressed CFI bytes | Savings |
| --- | ---: | ---: | ---: |
| Full four-ABI AAR | 6,296,743 | 6,078,384 | 218,359 (3.47%) |
| ARM64 native AAR entry | 1,318,593 | 1,239,268 | 79,325 (6.02%) |
| x86 native AAR entry | 1,456,801 | 1,382,775 | 74,026 (5.08%) |
| x86_64 native AAR entry | 1,382,836 | 1,317,828 | 65,008 (4.70%) |

The raw ARM64 ELF shrank from 2,606,128 to 2,203,488 bytes. ARMv7's library is
byte-identical between the two builds. Download savings for an application depend
on its ABI selection and packaging.
