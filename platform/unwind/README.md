# bd-unwind

Shared stack-unwinding primitives for the iOS profiler and the crash reporter. The crate is `no_std` and has no dependencies. It is not linked into either SDK consumer yet.

The first layer provides ARM64 registers, frame-pointer walking, pointer-authentication stripping, fault-tolerant current-task reads on Apple platforms, and a bounded Mach-O metadata reader. Compact unwind, DWARF and logical Swift async walking are subsequent layers. An async boundary is reported explicitly rather than followed as an ordinary frame pointer.

## Capture contract

- The caller captures registers and supplies a reader, known stack bounds and an output slice.
- A reader may access a stopped thread or a copied stack; it must fail instead of dereferencing unreadable addresses. The caller is responsible for consistency and image lifetimes.
- Walking uses no allocator, global mutable state, locks, logging or symbol resolution. Custom readers must preserve those properties when used in a crash or suspended-thread context.
- Only the returned prefix of the output is initialized. A stop reason distinguishes a terminated chain from unavailable FP, unreadable memory, invalid pointers, an async boundary and output limits.
- Addresses retain their PC/return-address distinction. LR is retained in the register context but is not guessed to be a caller without unwind rules. Inlining, tail calls and interrupted prologues/epilogues can therefore make this first FP-only layer incomplete.

`ImageInfo::read` runs during normal execution, before a capture. It copies the image UUID, __TEXT range and locations of __unwind_info and __eh_frame. It does not retain references into a live image, enumerate dyld images or guarantee that those sections remain mapped. The future consumers own snapshot/cache publication and lifetime management; a crash path must not initialize that cache.

Mach reads and the ARM64 PAC instruction have host tests, but this layer does not establish signal-handler safety, remote-thread capture correctness or physical-device coverage by itself. Those need tests with the actual signal/Mach/profiler callers. No thread suspension or monitor installation belongs in the walking API.

## Verification

```sh
cargo nextest run -p bd-unwind
cargo clippy -p bd-unwind --all-targets
./bazelw test //platform/unwind:bd_unwind_test
./bazelw build --config=clippy //platform/unwind:bd_unwind
```
