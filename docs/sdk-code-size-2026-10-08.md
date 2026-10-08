<!-- sdk-size-analysis:generated:start -->
<!-- sdk-size-analysis:inputs:5d5b75a7087874c3932d4c343cbc4c04993ea8f13217c0001aae16965c6371b6 -->
# Capture SDK Code Size: Pristine Baseline

Measured **2026-10-09** at SDK revision `12a2977ece84b0c90e4861aa6d5407abd3f2c12a` with committed dependency pins.
This baseline uses fresh replacement evidence. All Android and iOS artifacts were built
from a clean detached worktree in this run; no historical spike or retained app is an input.

## Findings

- Android ARM64: **1,658,296 bytes raw**, **1,040,719 bytes ZIP**
  (1,017 CI-style rounded-up KiB).
- Executable storage: **1,437,328 bytes**; `.text`: **1,435,776 bytes**.
- Logger/orchestration: **335,464 bytes (23.34%)**.
  The eight largest `bd_logger` ranges occupy **130,524 bytes**,
  **41.12%** of its label, not removable feature costs.
- Protobuf messages/runtime: **169,740 bytes (11.81%)**.
  Most message-labeled code is specialized container/support code, not named wire loops.
- iOS ARM64 static framework: **8,959,600 bytes**.
  Fresh stripped sample executable: **3,245,584 bytes**;
  normalized unsigned IPA: **1,581,007 bytes**.
  This is a local stripped-app proxy, not signed App Thinning or SDK-only app overhead.

## Inputs and Controls

| Input | Value |
| --- | --- |
| capture-sdk | `12a2977ece84b0c90e4861aa6d5407abd3f2c12a` |
| rust-protobuf | `4ea4e3845e1c743630c72b081763a2ccfeeace44` |
| shared-core | `caf73cfd5d8a417cffd1cef7256955b9f45348ef` |
| Build host | Darwin arm64 |
| Xcode | Xcode 27.0, Build version 27A266a |
| NDK | 27.2.12479018 |
| Analysis LLVM | 18.0.3 |
| Registered Rust toolchain | 1.98.0 |
| Android / iOS architecture | ARM64 / unsigned device ARM64 |
| Native compression | Bazel zipper `cC`; `jni/arm64-v8a/libcapture.so` |
| Cargo.lock SHA-256 | `4a46f5ee938abf5e829aa6404a381b0432e80064c9ff24babed5b33645a1f555` |
| MODULE.bazel.lock SHA-256 | `c9735930cd8ec5f023cb53a055e0aa4614b8404ae960dc8a6ad6a1247d437881` |
| Native build ID | `f608ba67d4541c49` |
| Native SHA-256 | `ff7d0276834b484bbe94cdcf96e8955ef90e3496718e8c0edee323061664469a` |

The evidence records all manifest/lock/BUILD hashes, dependency revisions, tool hashes,
analysis-script hashes and exact commands. Release flags use size optimization, one
codegen unit and fat LTO; Android forces unwind tables and compresses CFI, while iOS
disables Rust unwind tables and uses Swift size/WMO optimization. The SDK MODULE pins
the Rust toolchain; its hash is retained with the controls. `--config=nocache` disables
remote release caching, not local action/disk caching. No caches were cleared.

Local macOS ARM64/Xcode measurements are not Linux/x86_64 Android CI bytes or the
separately pinned CI Xcode output. No dependency wiring or user checkout was changed.

## Android Structural Accounting

| File Component | Bytes |
| --- | ---: |
| .text | 1,435,776 |
| .rodata | 101,376 |
| .gnu_debugdata | 51,640 |
| .data.rel.ro | 27,304 |
| .rela.dyn | 7,240 |
| .dynstr | 3,567 |
| .data | 3,496 |
| .dynsym | 3,360 |
| .rela.plt | 2,280 |
| .plt | 1,552 |
| .hash | 1,128 |
| .got.plt | 784 |
| .dynamic | 416 |
| .gnu.hash | 364 |
| .gnu.version | 280 |
| .shstrtab | 227 |
| .note.android.ident | 152 |
| .gnu.version_r | 96 |
| .note.gnu.build-id | 24 |
| .fini_array | 16 |
| .got | 8 |
| Headers and padding | 17,210 |
| **Raw total** | 1,658,296 |

NOBITS storage is **360 bytes**, excluded from the file total.
`.gnu_debugdata` is shipped compressed mini-debug metadata, not full DWARF. The
ZIP metric includes its complete archive overhead; compression is not additive by section.

## Android Executable Breakdown

| Whole-Function Label | Bytes | % |
| --- | ---: | ---: |
| Logger, configuration application, and upload orchestration | 335,464 | 23.34% |
| Rust standard library and generic support | 136,648 | 9.51% |
| Protobuf messages and type-specialized support | 133,728 | 9.30% |
| Async runtime, futures, and synchronization | 96,240 | 6.70% |
| Workflow engine and compiled workflow support | 87,144 | 6.06% |
| Android/JNI bridge | 83,060 | 5.78% |
| LLVM shared outlined functions | 82,284 | 5.72% |
| SDK common utilities and state | 63,232 | 4.40% |
| Metrics, statistics, and histograms | 55,048 | 3.83% |
| API transport and stream management | 49,984 | 3.48% |
| Crash and ANR processing | 37,256 | 2.59% |
| Time and filesystem support | 36,672 | 2.55% |
| Protobuf wire/serialization runtime and helpers | 36,012 | 2.51% |
| Artifact upload and persistence | 34,056 | 2.37% |
| Log buffers and ring-buffer storage | 33,976 | 2.36% |
| FlatBuffers schemas, verification, and runtime | 31,432 | 2.19% |
| Regex and text search | 24,188 | 1.68% |
| Log filters and matchers | 18,752 | 1.30% |
| Tracing and error handling | 18,420 | 1.28% |
| Hashing, randomness, UUIDs, and crypto | 15,820 | 1.10% |
| Serde, JSON, and other data formats | 11,244 | 0.78% |
| Compression and compression checksums | 8,144 | 0.57% |
| Runtime configuration | 5,640 | 0.39% |
| Linker stubs/other executable sections | 1,552 | 0.11% |
| Other native runtime and support | 852 | 0.06% |
| Session replay | 480 | 0.03% |

Each interval has one label; aliases are counted once and gaps remain explicit.
Schema/type references precede generic owners. Whole labeled functions can contain
inlined dependencies, and shared LLVM outliners are separate. These labels are not exact
LTO crate costs or independent feature savings. No DWARF inline provenance is available.

## Protobuf Decomposition

| Function Shape | Bytes |
| --- | ---: |
| Protobuf-type-specialized container and support functions | 100,224 |
| Generated merge/size/write methods | 23,572 |
| Other generated message functions | 9,932 |

Descriptor/reflection inventory: **1 symbol(s)**.
Full identities are retained in scratch analysis; absence of named descriptor graphs does
not establish an exact zero-byte reflection or constant-data cost. JNI descriptors are excluded.

## Logger Origins

| Origin Label | Bytes |
| --- | ---: |
| bd_logger | 317,452 |
| bd_log_primitives | 17,484 |
| bd_log_util | 416 |
| bd_events | 112 |

## Logger Modules

| Module | Bytes |
| --- | ---: |
| builder | 72,384 |
| async_log_buffer | 63,560 |
| consumer | 52,740 |
| device_command | 29,000 |
| client_config | 24,572 |
| workflow_attachment | 20,088 |
| flush_registry | 12,236 |
| service | 5,576 |
| log_replay | 5,188 |
| workflow_attachment_upload | 5,024 |
| metadata | 4,812 |
| state_upload | 4,704 |
| trigger_upload_artifact | 4,260 |
| logger | 3,216 |
| logging_state | 2,044 |
| network | 2,008 |
| battery | 1,180 |
| write_log_to_buffer | 1,164 |
| directory_lock | 1,104 |
| internal_report | 888 |
| upload_coordination | 668 |
| internal | 648 |
| device_id | 220 |
| buffer_selector | 96 |
| app_version | 72 |

## Largest Logger Functions

| Function | Bytes |
| --- | ---: |
| `<bd_logger::async_log_buffer::AsyncLogBuffer<bd_logger::log_replay::LoggerReplay>>::run_with_shutdown::<bd_crash_handler::Monitor>::{closure#0}` | 37,816 |
| `<bd_logger::builder::LoggerBuilder>::build::{closure#4}` | 27,600 |
| `<bd_logger::client_config::LoggerUpdate as bd_logger::client_config::ApplyConfig>::apply_configuration::{closure#0}` | 14,312 |
| `<bd_logger::builder::LoggerBuilder>::build` | 11,728 |
| `std::sys::backtrace::__rust_begin_short_backtrace::<<bd_logger::builder::LoggerBuilder>::run_logger_runtime<core::pin::Pin<alloc::boxed::Box<dyn core::future::future::Future<Output = core::result::Result<(), anyhow::Error>> + core::marker::Send>>>::{closure#0}, core::result::Result<(), anyhow::Error>>` | 10,952 |
| `<core::future::poll_fn::PollFn<<bd_logger::async_log_buffer::AsyncLogBuffer<bd_logger::log_replay::LoggerReplay>>::run_with_shutdown<bd_crash_handler::Monitor>::{closure#0}::{closure#1}> as core::future::future::Future>::poll` | 10,672 |
| `<bd_logger::consumer::BufferUploadManager>::handle_trigger_uploads::{closure#0}` | 8,736 |
| `<bd_logger::consumer::BufferUploadManager>::run::{closure#0}::{closure#0}` | 8,708 |

## Logger Direct Dependencies

| Separately Named Dependency | Bytes |
| --- | ---: |
| bd_workflows | 87,144 |
| tokio | 82,356 |
| bd_api | 48,088 |
| bd_client_stats | 41,592 |
| bd_artifact_upload | 34,056 |
| bd_buffer | 29,312 |
| bd_versioned_kv | 20,800 |
| notify | 17,036 |
| bd_client_common | 16,132 |
| bd_log_matcher | 15,880 |
| bd_crash_handler | 13,952 |
| bd_session | 12,504 |
| time | 10,572 |
| bd_state | 9,060 |
| anyhow | 8,408 |
| sha2 | 8,124 |
| bd_runtime | 5,640 |
| bd_event_buffer | 4,608 |
| flate2 | 4,412 |
| bd_error_reporter | 4,232 |
| bd_client_stats_store | 3,864 |
| parking_lot | 3,824 |
| base64 | 3,556 |
| uuid | 3,224 |
| bd_stats_common | 3,104 |
| bd_log_filter | 2,872 |
| bd_shutdown | 2,148 |
| bd_workflow_stats | 1,648 |
| bd_time | 1,592 |
| futures_util | 1,492 |
| tracing | 1,388 |
| bd_device | 1,328 |
| bd_backoff | 852 |
| bd_session_replay | 480 |
| log | 384 |
| bd_resource_utilization | 204 |
| bd_key_value | 144 |
| bd_completion | 60 |
| bd_internal_logging | 44 |

Dependencies in this table are already outside the logger bucket. Do not charge
their bytes to every caller, multiply them by static calls, or treat them as removal savings.

## iOS Fresh Measurements

| Metric | Bytes |
| --- | ---: |
| ARM64 static framework | 8,959,600 |
| Unsigned sample executable | 6,651,096 |
| Unsigned stripped sample executable | 3,245,584 |
| Stripped bundle file bytes | 3,247,237 |
| Normalized unsigned IPA | 1,581,007 |

## iOS Static Archive Components

| Largest Member/Component | Bytes |
| --- | ---: |
| capture_rust.capture_rust.10f225106d30cc2c-cgu.0.rcgu.o | 3,729,576 |
| archive_symbol_index | 903,208 |
| Capture.swift.o | 161,384 |
| WebVitalMessage.swift.o | 155,792 |
| compiler_builtins-5291df8b9c35768d.compiler_builtins.b5eabfcfe49d326e-cgu.0.rcgu.o | 103,872 |
| LoggerObjc.swift.o | 100,608 |
| NetworkRequestMessage.swift.o | 85,472 |
| Logger.swift.o | 83,008 |
| URLSessionNetworkClient.swift.o | 71,040 |
| LongTaskMessage.swift.o | 58,112 |
| WebViewIntegration.swift.o | 57,312 |
| CrashReporterService.swift.o | 53,200 |

Archive member bodies: **8,938,520 bytes**; archive headers/name storage/padding:
**21,080 bytes**. Member `__TEXT,__text` totals **1,984,280 bytes**.
The Rust LTO member includes dependencies; its name does not make all its bytes bridge code.
Linkable symbol indices, relocations and member metadata are not final app executable costs.

## iOS Linked Text Estimates

| Whole-Function Label | Bytes | % |
| --- | ---: | ---: |
| Swift functions and type-specialized support | 816,760 | 36.24% |
| Logger, configuration application, and upload orchestration | 325,912 | 14.46% |
| Other native runtime and support | 189,676 | 8.42% |
| Protobuf messages and type-specialized support | 128,600 | 5.71% |
| Rust standard library and generic support | 127,916 | 5.68% |
| Async runtime, futures, and synchronization | 94,252 | 4.18% |
| Workflow engine and compiled workflow support | 84,168 | 3.73% |
| SDK common utilities and state | 63,588 | 2.82% |
| Metrics, statistics, and histograms | 53,820 | 2.39% |
| API transport and stream management | 51,044 | 2.26% |
| Protobuf wire/serialization runtime and helpers | 34,680 | 1.54% |
| Artifact upload and persistence | 33,376 | 1.48% |
| Time and filesystem support | 33,036 | 1.47% |
| Log buffers and ring-buffer storage | 32,212 | 1.43% |
| FlatBuffers schemas, verification, and runtime | 28,672 | 1.27% |
| Objective-C methods | 27,196 | 1.21% |
| Regex and text search | 23,156 | 1.03% |
| Log filters and matchers | 18,100 | 0.80% |
| Tracing and error handling | 18,076 | 0.80% |
| Crash and ANR processing | 17,128 | 0.76% |
| Other dependencies | 14,600 | 0.65% |
| Other SDK support | 12,420 | 0.55% |
| Compression and compression checksums | 8,144 | 0.36% |
| Hashing, randomness, UUIDs, and crypto | 5,944 | 0.26% |
| Runtime configuration | 5,296 | 0.23% |
| Serde, JSON, and other data formats | 3,592 | 0.16% |
| Platform bridge | 1,668 | 0.07% |
| Session replay | 852 | 0.04% |

Linked `__TEXT,__text`: **2,253,884 bytes**; leading unattributed range:
**0 bytes**. Includes SDK, sample and support code.
Swift and Objective-C labels are separate from the native catchall. Next-address estimates
include alignment and anonymous trailing instructions; no SDK-free app control was built.

## iOS Stripped File Accounting

| Component | Bytes |
| --- | ---: |
| file_backed_sections | 2,936,924 |
| linkedit | 214,544 |
| headers_padding | 94,116 |

## iOS Symbol Metadata

| Component | Bytes |
| --- | ---: |
| original symbol_records | 507,456 |
| original symbol_strings | 2,987,448 |
| stripped symbol_records | 25,472 |
| stripped symbol_strings | 63,920 |

Stripping removes **3,405,512 file bytes**.
It preserves all **52 sections**, loader payloads/dependencies,
**1,589 undefined symbols** and **2,490 indirect targets**.
Symbol count: **31,716 -> 1,592**; exports:
**643 -> 2**, with surviving targets unchanged.
Ordinary Rust symbols are removed and dynamically referenced entries retained. Resource
bytes and normalized ZIP entry sets/CRCs match, and original executable bytes are unchanged.

Framework SHA-256: `694f69d9b0a229b646fe2258990732c5cae76fae5276c977c454e456459ad79c`.
Unsigned executable SHA-256: `e386b56b0c3bc3046f4886849d10339988e5f9b7d8f462d90ded9e30b3d6169d`.
Stripped executable SHA-256: `3b61931550e9a0bf0c3df96775d23f42b8b35926d263c2e55f1ace2981e420a8`.
Normalized IPA SHA-256: `c33f20f026e60c51f9f956874ffa383f78977caa923aa2d987e8bcfdb4d9d005`.

## Verification and Reproduction

Android audits: 12,394 defined symbols, 12,207 nonzero code symbols;
text gaps **0 bytes**, overlapping/alias ranges **0 bytes**.
Debug/shipped layouts and build IDs match; NOBITS allocated-byte identity is unverified. Uploaded debug-map
names, addresses and sizes match, allowing only known debug-only data-class changes.
AAR/native ZIP identity and CRC checks, framework member/size checks, archive analysis,
Mach-O accounting, stripping/resource audits and source snapshot preservation all passed.

The executable stripped-app bytes are not a signed export or App Thinning result.
No Rust/FFI behavior changes, device execution, CPU/allocation/throughput measurements,
signed exports, or unrelated SDK Clippy/behavior test validation are claimed.

Run the one-command workflow in [the size-analysis skill](../.claude/skills/sdk-size-analysis/SKILL.md).
The runner saves fresh Android/iOS artifacts, all logs, sections, symbols, intervals and
eight logger disassemblies under the chosen workspace `.tmp/` directory. It emits a
hash-checked report and [compact evidence](sdk-code-size-2026-10-08.evidence.json).
Use `--publish-only` to regenerate publication without rebuilding; it checks artifact
hashes and revision/lock agreement first. Exact commands and tool/script hashes are
recorded in the evidence. Existing variants are never overwritten.

## Next Investigations, Not Claimed Savings

1. Startup/runtime construction and the event loop are the largest logger ranges. Inspect
   their disassembly and matching pinned source before testing a shared non-inlined or
   erased startup boundary, or a cold control-path boundary. New async functions may inline
   again; boxing/no-inline can increase code size and change runtime costs.
2. Type-specialized protobuf support exceeds named wire methods. Test ownership/error
   conversion and specialization boundaries, not just a replacement parser. Preserve API,
   shutdown, ordering and cancellation behavior before measuring matched artifacts.
3. Swift code, reflection/type metadata and static archive symbol/relocation storage need
   separate investigation. Static archive reductions need not reduce a stripped app.
4. Runtime/dependency feature narrowing is a hypothesis, not the sum of named dependency
   bytes. Verify transitive users; retain raw, ZIP, code, data and metadata results for
   each isolated experiment, including rejected regressions.
<!-- sdk-size-analysis:generated:end -->

## Analyst Interpretation

Interpretation reviewed **2026-10-09** against this run's frozen artifacts and pinned
logger source, not the prior report. The October 8 filename is retained as requested;
the measured revision and date above identify the replacement baseline. The original
report remains in [the historical archive](sdk-code-size-2026-10-08.previous-675dd3a90889.md).

Local investigation evidence is in [the analysis packet](../.tmp/sdk-size-analysis/run-tsou9f33/results/ANALYSIS.md).
The adjacent compact evidence is durable; full artifacts, source snapshots, disassemblies
and logs remain workspace-local. Three independent detached-worktree collections
produced identical measured binaries and totals while validating tooling fixes. Local
build-cache hits are permitted; this is not a cache-free compiler reproducibility claim.
No baseline comparison or SDK optimization experiment was performed.

## Source and Disassembly Interpretation

**Startup/runtime.** The pinned `LoggerBuilder::run_logger_runtime` source spawns a
dedicated thread, builds a current-thread Tokio runtime with `enable_all`, and
`block_on`s the supplied future. Its 10,952-byte specialization includes runtime
construction and support code, not just logging. `LoggerBuilder::build` returns a
boxed future and already boxes the joined worker futures. Its 27,600-byte initialization
future acquires the directory lock, restores runtime configuration and state, constructs
upload/crash/API workers, and joins them. A blanket recommendation to box these futures
would duplicate existing boundaries. A cold startup helper is only a testable hypothesis;
fat LTO may inline it again, and changing erasure or allocation can worsen size or runtime.

See the frozen [builder source](../.tmp/sdk-size-analysis/run-tsou9f33/results/android/logger-source/builder.rs)
and [runtime-wrapper instructions](../.tmp/sdk-size-analysis/run-tsou9f33/results/android/analysis/logger-disassembly-04.txt).

**Event loop.** `AsyncLogBuffer::run_with_shutdown` and its select/poll helper total
48,488 Android bytes. The source multiplexes configuration, previous-run crash reports,
workflow completions, ingress/control batches, pipeline work, timers, resource reporting,
session replay, platform events and shutdown. Configuration publishes event limits before
opening the startup gate; shutdown stops platform callbacks before closing the event
buffer. These are correctness constraints on any extraction or specialization experiment.
The body contains 13 static direct calls to a 236-byte protobuf varint reader. This shows
serialization support inside a logger-labeled range; it does not make shared callee bytes
additive or measure execution frequency. The iOS event-loop body and builder initialization
future are also the two largest linked ranges, at 35,564 and 31,168 estimated bytes.

See the frozen [event-loop source](../.tmp/sdk-size-analysis/run-tsou9f33/results/android/logger-source/async_log_buffer.rs)
and [event-loop instructions](../.tmp/sdk-size-analysis/run-tsou9f33/results/android/analysis/logger-disassembly-00.txt).

**Configuration and uploads.** The 14,312-byte configuration future's source awaits
trigger-buffer reconciliation before rebuilding producers and publishing updates, so
recovered uploads can relock buffers before ordinary writers see new producers. The
upload manager filters and deduplicates eligible buffers, prunes stale durable references,
and persists the admitted set before starting workers. Its 8,736-byte trigger-upload body
has 12 static String-clone sites; the configuration body has nine. Shared ownership or
error-conversion boundaries are plausible experiments, not measured savings. Preserve
configuration ordering, crash/restart replay, failure completion and cancellation.

See the frozen [configuration source](../.tmp/sdk-size-analysis/run-tsou9f33/results/android/logger-source/client_config.rs),
[upload source](../.tmp/sdk-size-analysis/run-tsou9f33/results/android/logger-source/consumer.rs)
and [trigger-upload instructions](../.tmp/sdk-size-analysis/run-tsou9f33/results/android/analysis/logger-disassembly-06.txt).

**Protobuf.** The 100,224-byte type-specialized container/support label is much larger
than the 23,572-byte named merge/size/write label. The one reflection inventory match
is a 560-byte Debug formatter for `protobuf::reflect::error::ReflectError`; no named
generated descriptor graph is found. Replacing a wire parser alone does not target most
of the message-labeled bytes. Container/drop/error specializations and representation
choices deserve separate matched experiments, with generated decoding and public API
parity retained. Neither anonymous constants nor inlined wire code are fully attributed;
the symbol inventory is not proof of a zero-byte reflection footprint. This baseline
contains no protobuf replacement spikes.

**iOS.** Swift-labeled text occupies 816,760 estimated bytes, or 36.24% of the linked
sample's text. It includes SDK, sample and specialized support, not SDK-only overhead.
The 3,405,512-byte executable stripping reduction equals the reduction in symbol
records plus symbol strings; section bytes, loader payloads, undefined symbols and
indirect targets were audited unchanged. This is a structural audit, not execution on a
device. The stripped app still carries constants, Swift/Objective-C metadata, stubs,
link-edit data and alignment beyond its 2,253,884-byte text section. The static archive's
3,729,576-byte Rust LTO member and 903,208-byte symbol index are distribution costs,
not corresponding stripped-app savings. Never strip that linkable framework. An SDK-free
sample control and signed App Thinning export are separate experiments; neither was run.

## Workflow Validation

- The documented entry point completed Android and iOS collection and publication at
  HEAD without hand-built commands, output-path guesses, dependency rewiring or SDK edits.
- Android used uploaded debug symbols because the full ELF was unavailable. Symbol
  identities, build IDs, retained sections and layouts were audited; NOBITS allocated-byte
  identity remains unverified. Debug-only disassembly now uses audited symbol labels,
  rather than objdump's misleading nearest exported JNI function.
- The packet now exposes computed static call sites, reflection matches and iOS hotspots;
  durable publication also retains the promised local report/evidence pair. Legacy report
  migration no longer imports obsolete workflow notes or duplicated generated proposals.
- The final collection uses the corrected script hashes. Focused regression tests cover
  the corrections. No SDK behavior, ABI, CPU, allocation, throughput or signed thinning
  claim is inferred from these tooling-only checks.

## Experiment Decisions

No SDK optimization is enabled or claimed. Prioritize one cold startup/control-path
boundary or one protobuf ownership/support specialization at a time. Require matched
dependency/tool/build controls, generated-decoder parity where relevant, startup and
restart ordering checks, shutdown/cancellation checks and bridge/ABI validation before
accepting an SDK change. Keep raw, ZIP, instruction, data and metadata deltas separately,
including rejected regressions. Tokio/watcher feature narrowing requires checking
transitive users; runtime flags alone do not remove linked code. For iOS-only work,
compare both the framework and stripped app rather than treating archive bytes as app
savings. These remain proposed investigations, not measured improvements.
