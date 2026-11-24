# bd-unwind

ARM64 frame-pointer unwinding and Mach-O metadata reading. Keep API contracts in rustdoc.

- Keep this crate `no_std` and dependency-free. Useful reference code may be copied or adapted;
  include only what the supported functionality needs and preserve required license notices.
- Walking must use caller-owned registers, memory readers and output buffers. Do not allocate,
  acquire locks, log, resolve symbols, initialize caches or introduce global mutable state in the
  capture path. Custom readers used there must preserve these properties.
- Callers manage thread suspension and image lifetimes. Survey Mach-O metadata during normal
  execution, before capture.
- Preserve SDK build compatibility across targets. The current unwinder supports ARM64 only;
  successful x86_64 compilation does not imply x86_64 unwinding support.
- Validate Mach-O tables structurally. Do not introduce arbitrary format limits without evidence.
- Host tests do not establish signal-handler safety or device capture correctness.

Focused checks from the repository root:

```sh
cargo nextest run -p bd-unwind
cargo clippy -p bd-unwind --all-targets
./bazelw test //platform/unwind:bd_unwind_test
./bazelw build --config=clippy //platform/unwind:bd_unwind
```
