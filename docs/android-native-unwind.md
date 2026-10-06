# Android Native Unwind Metadata

Android release libraries retain compiler-generated unwind rules. ARM64, x86,
and x86_64 package those rules in XZ-compressed GNU MiniDebugInfo rather than
runtime-mapped unwind tables. ARMv7 retains its ARM exception-index tables.

## Supported ABIs

Compression is enabled only for `arm64-v8a`, `x86`, and `x86_64`. These ABIs use
DWARF `.eh_frame`, which the linker script and extraction pipeline below preserve
without rewriting unwind rules.

`armeabi-v7a` instead uses ARM EHABI (`.ARM.exidx`, `.ARM.extab`, and
`PT_ARM_EXIDX`). Its address-relative table references require a different
linker-layout and extraction strategy; the `.eh_frame` pipeline cannot be reused
unchanged. This is a limitation of this implementation, not a general lack of
ARMv7 support in GNU MiniDebugInfo.

A layout-preserving ARMv7 spike saved only 2,929 bytes (0.26%) under DEFLATE while
increasing the raw SDK library by 20,612 bytes. ARM32 runtime unwind validation
did not complete: the installed Android 12 ARM64 emulator images lacked ARM32
userspace, and the software-emulated alternative did not reach a working test.
ARMv7 therefore retains conventional EHABI tables rather than shipping an
unverified alternative with little measured size benefit.

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

Compression uses LZMA2 preset 9 with a 1 MiB dictionary rather than preset 9's
default 64 MiB dictionary. All three current CFI payloads are smaller than 1 MiB,
and this cap preserves their compressed sizes while reducing XZ-reported decoder
memory from 65 MiB to 2 MiB. This avoids declaring an unnecessarily large
dictionary for crash-time decompression.

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

Earlier ARM64 prototype tests also passed on an Android 16.1 emulator: compressed
GNU MiniDebugInfo reproduced baseline native PCs for both the fixture and SDK
fault, and no-CFI controls truncated the traces. Those tests used converted
`.debug_frame` records, not the final pipeline's preserved `.eh_frame` records;
the final pipeline's runtime verification was on Android 12.

All four ABI artifacts retain 16 KiB ELF load-segment alignment. Runtime checks
of the final pipeline used a 4 KiB-page Android 12 emulator, not a 16 KiB-page
device.

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
