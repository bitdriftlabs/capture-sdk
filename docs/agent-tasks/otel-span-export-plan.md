# OTel Span Export: Shared-Core vs. Per-Language Plan

This started as a planning document and is now also the status record for the work: see "Status"
below for what has been built and verified. It exists to answer one question in detail: **for the OTel span-export feature (emit an
OpenTelemetry `CLIENT` span per traced network request, carrying the same trace ID/span ID
already injected into that request's header, and export it via OTLP/HTTP to a user-configured
endpoint), how much of the implementation can live once in shared-core's Rust, and how much must
be duplicated per platform (Android/Kotlin and iOS/Swift)?**

The feature already exists as a working proof-of-concept, but only on Android, and entirely in
Kotlin — no Rust involvement at all today. This document is the result of a deep, code-grounded
investigation of both repositories (`shared-core` and `capture-sdk`) to answer that question
honestly, including surfacing the real costs of moving things into Rust, not just the benefits.

This document is written to be read top-to-bottom by someone with no prior context on this
investigation. It follows the same "layered plan" convention as
[`event-buffer-plan.md`](./event-buffer-plan.md) in this same directory: read the plan-at-a-glance
and recommendation first, then the milestone roadmaps, then the detailed reference sections if you
need the full reasoning or exact file paths.

A copy of this same document also lives in `capture-sdk` at
`docs/agent-tasks/otel-span-export-plan.md`, since the per-language work happens there. They are
kept identical on purpose — whichever repo you're looking at, you have the whole picture.

---

## Status (read this first)

**Android + shared-core (Path A) is built and manually verified end to end. iOS and the wrappers are
not started.** "Verified" means: with the rebuilt local AAR in the `bitdrift-shop-opentelemetry` demo app
(Android emulator) and a local ClickStack/HyperDX container, traced requests produced spans that
reached ClickStack, confirmed by hand. It does **not** mean tested in the automated sense — see
"Not yet verified" below.

| Piece | State |
|---|---|
| `shared-core`: `bd-otlp-traces::build_span_payload` | Done. Builds, Clippy-clean (repo nursery/pedantic config), license-header and nightly-rustfmt clean. No tests written. |
| Android JNI export `buildOtelSpanPayload` (`platform/jvm/core`) | Done. `cargo check`, `CARGO_BAZEL_REPIN=true ./bazelw build //platform/jvm/core:capture_core`, and `--config=clippy` all pass. |
| Android Kotlin (`platform/jvm/capture`) | Done. Gson OTLP models removed; `OtelSpanExporter` now calls the JNI function and POSTs the returned bytes once. |
| Demo app (`sa-public/misc-demos/bitdrift-shop-opentelemetry`) | Works with the rebuilt AAR. Header shows the AAR's SHA-256 prefix and a "Rust span builder: yes/no" check. |
| iOS (`platform/swift/source`) | Not started. |
| Wrappers (`platform/capture_flutter`, `platform/webview`) | Not investigated. They sit on the native layers, so they may inherit span export for free — confirm before assuming. |

**Where the code lives right now (for an agent picking this up on a different machine):** both
branches below are pushed to `origin` — no PR open on either yet, this is a personal/WIP pair, so
`git fetch origin <branch> && git checkout <branch>` in each repo is all that is needed to
continue. Same branch name in both repos on purpose, to make the pairing obvious.

- **`shared-core`**, branch `slerner/otel-span-export-shared-core`. The `bd-otlp-traces` crate
  itself is unchanged since commit `d8b28785` (later commits on this branch, including this one,
  only touch the plan doc) — don't trust any specific commit SHA named in this doc as "current
  HEAD" (a doc commit updating that claim is immediately one commit stale); run
  `git log --oneline origin/slerner/otel-span-export-shared-core` in `shared-core` for the real
  answer.
- **`capture-sdk`**, branch `slerner/otel-span-export-shared-core`, based on `origin/main`. Two
  commits: `459077d4` (the original Android POC, cherry-picked from a now-deleted branch) and
  `c225e462` (the Rust/Kotlin wiring to `bd-otlp-traces` + this plan doc, copied to
  `docs/agent-tasks/`). Its `Cargo.toml` pins every `shared-core` rev to `74c81ef1`, which still
  resolves correctly since the crate content hasn't changed at any later `shared-core` commit
  above — **but if you push a `shared-core` commit that changes actual code**, bump the `rev` in
  `capture-sdk/Cargo.toml` to match (see "Cross-repo build order" below) and re-run with
  `CARGO_BAZEL_REPIN=true`.
- **A history note, not a live pointer:** an earlier version of this plan referenced a
  `shared-core` branch that was pushed, then deliberately deleted, then re-created fresh under the
  same branch name at a new commit. If you see a `shared-core` commit SHA referenced anywhere
  outside this doc (an old local clone, a stale note) that doesn't appear in
  `git log origin/slerner/otel-span-export-shared-core`, it's from the deleted branch and no
  longer exists on the remote.
- `sa-public` branch `slerner/bit-9050-otel-demo-fixes` (PR #72) is unrelated to this cleanup and
  untouched.

### What we learned building Android (reuse this for iOS and the wrappers)

- **FFI shape that keeps the ABI stable:** one stateless call, span/resource attributes passed as three
  parallel arrays each (String keys, String values, byte type tags: 0 = string, 1 = int, 2 = bool). Values
  always cross as strings and are re-typed in Rust, so adding an attribute is a platform-only change and never
  touches the FFI signature. Span kind is hardcoded to `CLIENT` in the bridge (no parameter); status maps
  0/1/2 to Unset/Ok/Error in the bridge.
- **The Rust bridge is small:** a helper (`otlp_attributes_from_arrays` in `platform/jvm/core/src/ffi.rs`) plus
  one export in `jni.rs`; new symbols must also be added to `platform/jvm/jni_symbols.lds` or they are stripped
  from the Linux/Android `.so`. The Swift equivalent is the `capture_*` C export in
  `platform/swift/source/src/bridge.rs` plus its declaration in `CaptureRustBridge.h` and the Swift call site.
- **Cross-repo build order:** commit and push `shared-core` first, bump every `rev` in `capture-sdk/Cargo.toml`
  to that commit (they must all match), add the new crate as a workspace dependency, then run the first Bazel
  build with `CARGO_BAZEL_REPIN=true`. Do not hand-edit `Cargo.lock` / `MODULE.bazel.lock`.
- **A missing `catch (UnsatisfiedLinkError)` would be a crash risk:** a Kotlin/Rust signature mismatch only
  fails at runtime. The Android exporter catches it and reports via the error handler instead.
- **Verifying the AAR is the new one:** the native `.so` must contain the `buildOtelSpanPayload` symbol
  (`strings jni/arm64-v8a/libcapture.so | grep buildOtelSpanPayload`) and `classes.jar` must not contain the old
  Gson `otel/ResourceSpansPayload`. The demo app automates this on screen (AAR hash + Rust check).
- **Build environment gaps found on a fresh machine** (fix once): `cargo-nextest`, `cmake`, `protoc`, git
  submodules (`git submodule update --init --recursive` in `shared-core`), and an exact-version `flatc`
  (`shared-core` pins 25.9.23 — build it from `thirdparty/flatbuffers` rather than using Homebrew's newer one),
  NDK 27.2, and `JAVA_HOME` set to JDK 17 for Gradle. `make format` in `capture-sdk` needs `buildifier`; run
  `./bazelw run //:rustfmt` and `./bazelw run //:ktlint_fix_all` directly instead.
- **OTLP JSON specifics are spec-mandated, not collector quirks:** hex `traceId`/`spanId`, integer enums,
  lowerCamelCase, int64 as strings (see the `bd-otlp-traces` crate docs).

### Not yet verified

- **No automated tests** were written or run for `bd-otlp-traces`, the JNI bridge, or the Kotlin exporter.
- **No offline check:** the airplane-mode behaviour (export fails immediately and silently, traced request
  unaffected) has not been exercised on a device.
- **Only arm64-v8a** was built/run (Apple-silicon emulator). x86_64/other ABIs untested.
- **Release-quality concerns not addressed:** the API is still `@ExperimentalBitdriftApi`; the `shared-core` pin
  points at a commit that is not on any remote; no `CHANGELOG.md` entry (required for user-facing behaviour
  changes in `capture-sdk`); no size-delta check against the CI binary-size reports.

---

## Plan at a glance

**Spans are time-sensitive in a way logs are not, and the plan below is built around that.** A
span only has value if it can still be stitched into the same trace as the backend spans it's
meant to give context to. Distributed tracing backends (ClickStack/HyperDX included) assemble a
trace within a bounded time window and then move on — a span that shows up minutes or hours later,
once the device reconnects, either arrives as an orphaned fragment nobody can correlate, or lands
in a trace nobody is looking at anymore. There is no "eventually consistent" value here the way
there is for a log line. **So: one export attempt, made immediately when the traced request
completes. If it succeeds, great. If it fails for any reason — including "device is offline" —
drop the span and move on. No queueing, no persistence, no retry-on-reconnect.**

This is also the correct reading of "behave like a network request when offline": a traced network
request doesn't queue and retry in the background when offline either — it just fails, right away,
and the SDK observes and logs that failure. Span export should fail the same way.

This has a large, welcome side effect on the rest of this plan: it removes almost the entire
"offline-resilience engineering" problem that an earlier draft of this plan was built around
(durable queues, backoff-on-reconnect retry, either per-platform or in Rust). What's left is much
smaller and much easier to reason about on both the per-language and shared-core sides.

### What can move to Rust, and why

One piece of the feature is real, non-trivial logic that would otherwise need to be written twice
(once in Kotlin, once in Swift) and kept in sync forever:

- **Building the OTLP span/resource payload** — turning "here's a completed HTTP request, here's
  its trace ID and span ID, here's some attributes" into the exact JSON (or protobuf) shape that
  OTLP collectors expect. This is pure data transformation with no OS dependency once the raw
  ingredients are in hand, and it's a real, fiddly, easy-to-drift-out-of-sync piece of logic (field
  names, OTel semantic-convention mappings, status-code translation, protocol-string parsing) —
  exactly the kind of thing worth having one correct implementation of rather than two.

Because there's no retry/queue requirement anymore (see above), this is a **much smaller, much
lower-risk** Rust candidate than it would have been otherwise: it can be a single, synchronous,
stateless "given these fields, return these bytes" function, called once per completed traced
request. Rust doesn't need to hold any state, retry anything, or call back into the platform
later — the platform still makes the one HTTP attempt itself, immediately, using its own native
HTTP client, exactly as it does today.

### What cannot move to Rust, no matter what

- **Trace ID / span ID generation** stays exactly where it is today (`SecureRandom` on Android,
  `SecRandomCopyBytes` on iOS). It's small, cheap, already works, and there is no shared logic to
  deduplicate — moving it to Rust would only add FFI surface for no benefit.
- **Gathering OS-specific attributes** — network connection type, cellular carrier, foreground/
  background app state, device model/manufacturer, OS version — must stay per-platform, because
  Rust has no access to `ConnectivityManager`/`TelephonyManager` (Android) or
  `CTTelephonyNetworkInfo`/`UIApplication.shared.applicationState` (iOS). These are OS APIs with no
  Rust-reachable equivalent. Both platforms already gather most of these values today for other,
  existing OOTB log fields — this plan does not require any *new* attribute gathering, just handing
  already-known values to whichever layer builds the final payload.
- **The actual HTTP POST of the export request, and the decision to drop on failure.** Shared-core
  deliberately does not vendor an HTTP/TLS client into the mobile binary (see detailed reference,
  §2) — the mobile SDK always performs real network I/O through the platform's native stack
  (OkHttp/URLSession). This plan follows that same precedent. Since there's no retry/queue, the
  platform doesn't need Rust to tell it when to retry, either — it just tries once and, on failure,
  discards the payload.

### The recommendation

Given how small the remaining Rust candidate is (one stateless payload-building function, no
persisted state, no callback trait, no configuration crossing FFI), **this is a case where doing
the Rust version directly is reasonable, not just a "maybe someday" deferred option.** The FFI cost
that made this expensive in the original version of this plan was almost entirely the retry/queue
machinery, which no longer exists in the design. That said, this is still new FFI surface on a
still-experimental feature, so two paths remain legitimate — pick based on how much you value "one
implementation, no drift risk" versus "zero new FFI work, ship faster":

**Path A (do the Rust version now): one new, small FFI function** — see "New FFI surface" below.
Both platforms gather the same inputs they already gather today, call this one function, get back
OTLP bytes, and POST them once via their own native HTTP client. No retry logic to write on either
platform at all.

**Path B (per-platform, no Rust changes): port the existing Kotlin payload-building logic to
Swift by hand**, and accept that the two implementations may drift over time as OTel semantic
conventions evolve or a bug is found in one but not the other. This avoids any new FFI work, at the
cost of the exact "written twice, QA'd twice, drifts apart" problem this whole investigation set
out to avoid — but for a small, well-bounded amount of logic (roughly 150 lines total across
`OtelSpanBuilder.kt`/`OtelResourceAttributes.kt`/`OtelSpanModels.kt` today), which is a much easier
duplication to accept than the fuller (queue-and-retry-included) feature would have been.

Either way, the offline-behavior design (try once, drop on failure, no queue) is identical on both
platforms and doesn't depend on which path you pick for the payload-building logic — see "Offline /
device-health behavior" below.

---

## The real cost of new FFI surface (still real, just much smaller than it first looked)

This repo's own `CLAUDE.md` (in `capture-sdk`) states plainly: *"Changes at the Swift C bridge or
Android JNI boundary are release-critical. Treat a function signature as an ABI contract... When
changing an FFI entry point, inspect and update every layer together."* This is not boilerplate
caution — it's borne out by history. Two recent, real commits that each added new but *simple*
Rust-owned capability (`f8353737`, exposing one existing boolean read via FFI; `456e30d3`, adding
mobile session configuration) each touched **33 and roughly 50 files respectively**, across the
Rust export itself, the ABI header (`CaptureRustBridge.h`), the Android JNI symbol allowlist, both
platforms' wrapper/interface layers, and both platforms' test mocks.

The single function this plan now needs — `build_otel_span_payload(...) -> bytes`, stateless,
synchronous, no callback, no persisted config — is structurally much closer to the existing simple
accessors (`getDeviceId`, `getSessionId`) than to the larger `456e30d3`-style example: one new Rust
export, one new header declaration, one new Kotlin `external fun`, one new Swift call site, and the
corresponding test mocks on each side. Expect something closer to the `f8353737` example's scale
than the ~50-file one — and even that number is mostly test-mock/wrapper boilerplate repeated
across two platforms, not genuinely hard engineering. But it's real ceremony either way, and every
layer still has to be updated together per the ABI-safety rule above — this is why Path B (skip
Rust entirely) remains on the table as a legitimate choice, not just a fallback.

---

## Shared-core plan (Path A)

This section describes what would be built in `shared-core` if Path A (do the Rust version now) is
chosen. If you're doing Path B (per-platform port) instead, skip straight to "Per-language plan"
below — nothing in this section is required for Path B.

### A new payload builder — built (`bd-otlp-traces`)

Shared-core already contained a crate, `bd-otlp-metrics`, that looked like the right *shape* to
model the payload-construction piece after — except it exports *metrics*, not *traces/spans*, and
its `DeliveryEngine`/`OffloadQueue`/retry machinery turned out not to be needed at all, since
there's no deferred delivery in this design.

**Built as a new crate, `bd-otlp-traces`, rather than real Protobuf codegen (deviating from the
original milestone below, deliberately):** the milestone as originally written called for vendoring
`trace.proto`/`trace_service.proto` via the same build-time codegen `bd-otlp-metrics` uses. In
practice, generic protobuf-JSON serialization of those types would produce the wrong wire format —
OTLP's JSON encoding is not the same as standard protobuf-JSON mapping. Per the OTLP specification
(`opentelemetry-proto/docs/specification.md`, "JSON Protobuf Encoding"), `traceId`/`spanId` must be
"case-insensitive hex-encoded strings," not the base64 a generic/naive protobuf-JSON serializer
would produce for a `bytes` field, and enum fields (`kind`, `status.code`) "MUST be encoded as
integer values," never enum name strings. A generic protobuf-JSON path would need this exact same
special-casing to be spec-compliant anyway, so hand-writing `serde`-based wire structs that get
this right directly is not a shortcut relative to "real" Protobuf codegen — it produces the same,
correct result with less machinery (no new build-time codegen step, no new build dependency).

`bd-otlp-traces/src/lib.rs` contains: `Attribute`/`AttributeValue` (OTLP `KeyValue`/`AnyValue`),
`SpanKind`/`StatusCode` (with correct OTLP wire-value mappings), `SpanExportRequest` (the flat,
platform-neutral input), and the public function `build_span_payload(&SpanExportRequest) -> Vec<u8>`
— pure, synchronous, stateless, holds no config, performs no I/O. Builds clean (`cargo build`),
lints clean under this repo's nursery/pedantic-level Clippy config, passes the license-header
check, and is formatted per the repo's (nightly) `rustfmt.toml`. Registered as a workspace member in
the root `Cargo.toml`; depends only on already-pinned workspace deps (`serde`, `serde_json`) — no
new third-party dependency.

**Milestone roadmap:**

1. [x] ~~Vendor trace protos~~ — superseded; see above. No protobuf codegen was added.
2. [x] **Span/resource payload builder.** Done — `bd-otlp-traces::build_span_payload`. A direct,
   mechanical port of the logic already proven out in Android's
   `OtelSpanBuilder.kt`/`OtelResourceAttributes.kt` (see detailed reference, §9).
3. [~] **New FFI surface.** Android JNI done (see "New FFI surface" below). Swift bridge not started. This is
   `capture-sdk`-side work (the JNI/Swift bridges live there, not in `shared-core`) and requires `capture-sdk`
   to depend on this crate via the cross-repo `rev` workflow in `capture-sdk/CLAUDE.md`.

### New FFI surface (only needed for Path A)

One addition:

- **As built (Android JNI):**
  `buildOtelSpanPayload(traceId, spanId, scopeName, name, startTimeUnixNano, endTimeUnixNano, statusCode,
  statusMessage, attributeKeys[], attributeValues[], attributeValueTypes[], resourceAttributeKeys[],
  resourceAttributeValues[], resourceAttributeValueTypes[]) -> byte[]?` — a single, synchronous, stateless
  call. `kind` is not a parameter (always `CLIENT`). The platform calls it once per completed traced request from
  the hook point that exists today (`CaptureOkHttpEventListener.callEnd()`/`callFailed()` on Android,
  `URLSessionTaskTracker`'s `didFinishCollecting` on iOS), passing whatever it already gathered. Rust hands back
  the OTLP bytes (or null on error); the platform then makes the one HTTP POST attempt itself via its native
  client and drops the payload on any failure. No retry, no queue, no callback trait, and no export
  configuration (endpoint/auth header) reaches Rust, since Rust never sends anything itself.

---

## Per-language plan (needed on both paths, with one caveat noted below)

### Android (`platform/jvm/capture`)

The feature already exists here. Its current fire-and-forget, no-retry `OtelSpanExporter.kt`
behavior is, under the corrected design above, **already correct** — a completed request's span
either sends successfully or it doesn't, and there is nothing further to fix about offline behavior
on Android. What remains:

1. [x] **(Path A) Replace the Kotlin payload-building logic** with a call to the Rust function. Done:
   `OtelSpanModels.kt` now holds `OtelAttributes` (parallel arrays) and `OtelSpan`; `OtelSpanBuilder` and
   `OtelResourceAttributes` fill them; `OtelSpanExporter` calls `CaptureJniLibrary.buildOtelSpanPayload` and
   POSTs the bytes. Attribute gathering, the OkHttp POST, and drop-on-failure are unchanged.
2. [x] ~~(Path B only) No change needed~~ — not taken.
3. [~] **Verify.** Manual end-to-end check passed (spans reach ClickStack from the demo app with the rebuilt
   AAR). Still open: unit tests, the airplane-mode offline check, and non-arm64 ABIs (see "Not yet verified").
4. [ ] **Productionize:** commit, push `shared-core` and repoint the `Cargo.toml` pin at a merged commit,
   `CHANGELOG.md` entry, size-delta review, decide whether the API stays experimental.

### iOS (`platform/swift/source`)

No implementation exists yet. The underlying trace-header injection infrastructure already exists
and is structurally equivalent to Android's:

- `TracePropagation.swift` already generates and carries a `URLSessionTraceContext` (trace ID +
  span ID) per traced request, exactly like Android's `TraceContextFactory`/`TraceContext`.
- `URLSessionTaskTracker.swift`'s `task(_:didFinishCollecting:)` is the structural equivalent of
  Android's `CaptureOkHttpEventListener.callEnd()`/`callFailed()` — timing, status code, byte
  counts, and the stashed trace context are all present simultaneously at this one call site.

**Recipe, mirroring what was done on Android** (do these in order; each bullet names the Android file it copies):

1. `shared-core` must be reachable by `capture-sdk` (pushed commit or merged), and `bd-otlp-traces` added as a
   `capture-sdk` workspace dependency and to the Swift bridge crate's `Cargo.toml` (Android: `platform/jvm/core/Cargo.toml`).
2. In `platform/swift/source/src/bridge.rs`, add one `extern "C"` export that takes the same inputs as the JNI
   function (trace/span ID, scope name, name, start/end nanos, status code, optional status message, and the two
   attribute triples of keys/values/type tags), builds a `bd_otlp_traces::SpanExportRequest` with
   `SpanKind::Client`, calls `build_span_payload`, and returns the bytes (Android: `jni.rs`
   `Java_..._buildOtelSpanPayload`; type tags 0/1/2 must match `OTLP_ATTRIBUTE_TYPE_*` in `ffi.rs`).
3. Declare it in `platform/swift/source/CaptureRustBridge.h`, add the Swift call site in `LoggerBridge.swift`, and
   follow the `CLAUDE.md` FFI/ABI-safety rules exactly (no extra trailing params, nullable `NSString *` for
   optional strings, update every layer together). Run focused bridge compilation, not just `cargo check`.
4. Build the attribute arrays in Swift from `HTTPRequestInfo`/`HTTPResponse`/`HTTPRequestMetrics` and iOS network
   and app-state APIs, at `URLSessionTaskTracker.task(_:didFinishCollecting:)`; POST once via the shared
   `URLSession`, tag the request so the SDK does not trace its own export (Android: `InternalTelemetryRequestTag`),
   and drop on any failure.
5. Manual check: iOS simulator + local ClickStack, trace appears; then the airplane-mode check.
6. First Bazel command after the rev bump needs `CARGO_BAZEL_REPIN=true`; use
   `./bazelw test //test/platform/swift/unit_integration/core:test --ios_simulator_device="iPhone 17"` for the iOS tests.
   Xcode here is 27.0 vs. the documented 16.2 — expect to look at the linker/toolchain first if the iOS build misbehaves.

**Wrappers (`platform/capture_flutter`, `platform/webview`):** not investigated. First question to answer: do they
route network requests through the native Android/iOS instrumentation (in which case span export is inherited
once the native layers ship it) or do they have their own network path that would need its own hook? Only then
decide whether they need any work.

**Milestone roadmap:**

1. [ ] **(Path A)** Call the new `build_otel_span_payload` FFI function from
   `didFinishCollecting`, passing the same inputs Android passes. **(Path B)** port
   `OtelSpanModels.kt`/`OtelSpanBuilder.kt`/`OtelResourceAttributes.kt` to Swift by hand instead —
   per detailed reference §9, this logic is OS-independent pure data transformation, so it's a
   faithful line-by-line port either way (the one Android-only dependency, `okhttp3.HttpUrl`, has
   an obvious `URL`-based Swift equivalent).
2. [ ] **Gather the OS-specific attributes iOS needs**, using iOS's existing equivalents of
   Android's `ClientAttributes`/`NetworkAttributes` (device model/OS version already gathered today
   for other OOTB fields; network connection type via `NWPathMonitor`, cellular carrier via
   `CTTelephonyNetworkInfo`, foreground/background state via
   `UIApplication.shared.applicationState`). This is the iOS equivalent of what Android already
   does for other fields, applied to this feature — not new gathering logic in spirit.
3. [ ] **Add `OtelExportConfiguration` to `Configuration.swift`**, matching Android's shape
   (endpoint URL, auth header name/value), and wire it into `Logger.start()`'s existing
   `Configuration` handling. This config stays entirely on the Swift side — it's just "where does
   this platform's own HTTP POST go" — regardless of which path you picked above; it never needs
   to cross FFI.
4. [ ] **Hook into `URLSessionTaskTracker`'s `didFinishCollecting`**: build the payload (via FFI or
   the ported Swift code), POST it once via the SDK's existing shared `URLSession`, and drop it on
   any failure — no retry, no queue, matching Android.
5. [ ] **Verify** with the same offline manual test as Android, plus the standard iOS unit/
   integration test locations (`test/platform/swift/unit_integration/`).

---

## Offline / device-health behavior

This section exists because the requirement was stated explicitly and deserves a precise, complete
answer.

**The rule: one export attempt per completed traced request, made immediately, using the
platform's already-available native HTTP client. If it fails for any reason — no connectivity, DNS
failure, destination down, timeout, whatever — drop the span. Do not queue it. Do not persist it.
Do not retry it later.**

This is a deliberate, load-bearing design decision, not a shortcut, and it's worth being explicit
about why it's *correct* rather than merely convenient:

- **A late span has no value.** Distributed tracing backends assemble a trace within a bounded time
  window and move on. A span for a request that happened while the device was offline, delivered
  minutes or hours later once connectivity returns, will very likely never be correlated with the
  backend spans it was supposed to give context to — either the backend has already finalized and
  evicted that trace's assembly window, or whoever would have found it useful has moved on. There
  is no "eventually consistent" value here the way there is for a log line. Building durable
  queue/retry infrastructure for this would be real engineering effort spent producing data nobody
  can use.
- **This is the correct reading of "behave like a network request when offline."** A traced network
  request, when the device is offline, does not queue and retry in the background either — it just
  fails, right away, and the SDK observes and logs that failure as a normal failed request. Span
  export should fail the same way: immediately, visibly (to internal error handling, same as
  today), and without lingering.
- **This keeps the non-blocking-admission property trivially true.** Because there's no queue,
  there's nothing to admit non-blockingly in the first place — the one export attempt is already
  async (OkHttp's `enqueue`/URLSession's task-based API), and "drop on failure" requires no
  additional synchronization or locking.
- **This keeps memory/disk impact at zero, not just bounded.** A device with degraded connectivity
  making many traced requests will attempt (and drop) many span exports, but never accumulate a
  backlog on disk or in memory — there's nothing to accumulate.
- **This preserves the one property that was never in question.** Export failure or slowness must
  never affect the *traced request itself*. The traced network request is real user/app traffic;
  span export is telemetry about it, generated only after the request has already completed. This
  was already true structurally in today's Android POC and remains true here.

This is a *narrower* requirement than "behave exactly like `bd-buffer`'s log upload pipeline,"
which is what an earlier draft of this plan aimed for. That pipeline's durability (queue, persist,
retry-with-backoff-on-reconnect) exists because logs retain their value no matter when they're
eventually delivered. Spans don't have that property, so they don't need that pipeline's
guarantees — they need the *simpler* guarantee ordinary traced network requests already have: try
once, fail fast, don't let failure cascade into anything worse.

---

## Detailed reference

This is the raw findings from the investigation, preserved for anyone who wants to verify a claim
above or go deeper than the plan sections needed to.

### §1. Repo/crate layout (shared-core)

`shared-core` is a flat Cargo workspace — all ~65 crates are top-level directories, listed as
`[workspace] members` in `Cargo.toml`. There is no `core/` subdirectory (capture-sdk's own
`CLAUDE.md` project-structure diagram has a stale reference to one).

The crates `capture-sdk`'s `CLAUDE.md` names as dependencies, and what they actually do:

- **`bd-logger`** — the logger engine. Ingests log calls via `EventBuffer`, manages
  `bd_session`/`bd_state` snapshots, and owns `ContinuousBufferUploader`
  (`bd-logger/src/consumer.rs:1314`), which drains a `bd_buffer::CursorConsumer` and feeds
  uploads to `bd-api`.
- **`bd-buffer`** — the memory-mapped, persistent/volatile ring buffer
  (`bd-buffer/src/buffer/{volatile,non_volatile,aggregate,common}_ring_buffer.rs`) with
  overwrite-on-full semantics and loss-tracking stats. This is the actual on-disk log queue.
- **`bd-api`** — owns the single persistent stream to bitdrift's backend (`ApiService/Mux`) and
  all reconnect/backoff/offline logic (`bd-api/src/{api,reconnect,network_quality,upload}.rs`).
- **`bd-crash-handler`** — native crash detection/report discovery and config writing.
- **`bd-runtime`** — client for bitdrift's server-pushed remote feature-flag/config system (not a
  place end users configure things like an OTLP endpoint — it's for bitdrift-controlled rollout
  flags).

Other crates directly relevant here: `bd-hyper-network`/`bd-noop-network` (network transport
implementations), `bd-artifact-upload` (generic file-upload queue), `bd-otlp-metrics` (a full,
currently-unused OTLP *metrics* exporter — see §6), `bd-session`, `bd-device`.

### §2. Existing network upload / offline handling (shared-core)

**HTTP/network client.** `bd-api`'s `Api::start_stream` is generic over a `PlatformNetworkManager`
trait. Shared-core ships one concrete implementation, `bd-hyper-network` (`hyper` +
`hyper-rustls`), but **capture-sdk does not use it for the actual mobile SDK builds**. Both
platforms supply their own native `PlatformNetworkManager`:

- Android: `platform/jvm/core/src/jni.rs:452`, bridging to Kotlin's `ICaptureNetwork`, backed by
  OkHttp.
- iOS: `platform/swift/source/src/bridge.rs`, `SwiftNetworkHandle`, backed by `URLSession`.
- `bd-hyper-network` is only pulled in by capture-sdk's `test/benchmark/Cargo.toml` — a
  benchmarking harness, not shipped code.

This is the strongest existing precedent for the "never vendor a Rust HTTP/TLS stack into the
mobile binary" conclusion used throughout this plan.

**Offline/retry mechanism**, fully documented in `bd-api/AGENTS.md` and verified against the code:

- A single persistent stream is maintained by `Api::maintain_active_stream()`, looping forever
  over `do_stream(...)`.
- Two independent delay layers gate reconnects: a min-reconnect-interval gate
  (`ReconnectState::next_reconnect_delay` in `reconnect.rs`) and exponential backoff with jitter on
  stream errors (`bd_backoff::ExponentialBackoff`: 500ms initial, 20-minute max, ×5.0 multiplier,
  honors server `retry_after`).
- Connectivity awareness is reactive, not predictive: `NetworkQuality::{Online,Offline,Unknown}`
  (`bd-network-quality/src/lib.rs`) is inferred from stream success/failure and time-since-last-
  connection (`Offline` after a 15s disconnect grace period).
- The actual queued data lives in `bd-buffer`'s mmap-backed ring buffer, which silently
  overwrites/evicts oldest records under pressure rather than blocking or crashing.
- `bd-artifact-upload` (used today for crash reports, state snapshots, workflow attachments) is a
  second, generic-looking disk-persisted upload queue with its own checksummed/compressed index,
  startup replay, and `ExponentialBackoff`/`InfiniteBackoff` retry.

**Is this pipeline generic, or bound to bitdrift's own protocol?** Tightly bound. `bd-api`'s
`DataUpload` enum is a closed set of bitdrift-specific protobuf message types
(`LogsUpload`, `StatsUpload`, `SankeyPathUpload`, `ArtifactUpload`, `DebugData`,
`DeviceCommandUpdate`), all multiplexed over the single `ApiService/Mux` stream to bitdrift's own
ingestion host. Even `bd-artifact-upload`, despite accepting arbitrary bytes, always wraps its
payload in an `UploadArtifactRequest` protobuf sent down that same stream — never to a third-party
endpoint. **This existing pipeline cannot be directly reused for OTLP export to an arbitrary
user-configured destination**; its queueing/backoff/persistence *patterns* are reusable as a
design template (and are exactly what this plan reuses), but the transport/wire-format machinery
itself is not.

### §3. Existing trace ID / span ID generation

Confirmed 100% platform-native today:

- Android: `TraceContextFactory.kt`, `generateTraceContext()` via `java.security.SecureRandom`.
- iOS: `TracePropagation.swift`, `URLSessionTraceContext.make()` via `SecRandomCopyBytes`.

A repo-wide grep in shared-core for `trace_id`/`span_id`/`traceparent`/`w3c`/`b3`/`datadog`/
`opentelemetry` found nothing related to this feature (the only `span_id` hits are in
`bd-proto/.../public_api_with_source/spans.rs`, bitdrift's own server-side Timeline search-spans
API — an unrelated concept).

Contrast with session ID, the working precedent for "Rust owns an ID, exposes it via FFI":
`bd-session::Strategy::session_id()` is called from
`Java_io_bitdrift_capture_CaptureJniLibrary_getSessionId` and the Swift equivalent
`capture_get_session_id`.

### §4. Existing device/resource attribute gathering

| Attribute | Rust core today? | Notes |
|---|---|---|
| Device ID | Yes — `bd-device`, exposed via JNI/bridge | Already crosses FFI |
| Session ID | Yes — `bd-session` | Already crosses FFI |
| App version / SDK version / OS version / device model/manufacturer | No | OS-API-bound (`Build.MODEL`, `PackageManager`, etc. on Android; iOS equivalents), gathered per-platform today for other OOTB fields |
| Network connection type / carrier | No | `bd-network-quality` only models coarse Online/Offline/Unknown from stream health, no transport-type/carrier concept. Confirmed OS-API-bound on Android (`ConnectivityManager`/`TelephonyManager`, gated behind `READ_PHONE_STATE`) |
| Foreground/background app state | No | `bd-resource-utilization` only provides a sampling *timer*, not the actual state; platform does the sampling |

### §5. FFI/bridge patterns and their cost

New Rust capability exposed to both platforms follows a consistent pattern: a Rust
`extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_*` in
`platform/jvm/core/src/jni.rs`, paired with a Kotlin `external fun` in `CaptureJniLibrary.kt`, and
an analogous `extern "C" fn capture_*` in `platform/swift/source/src/bridge.rs`, declared in
`CaptureRustBridge.h` and called from a Swift wrapper.

Two real historical examples of the cost of this, from `git log`:

- `f8353737` ("Fully expose isTracingActive for iOS + add clearEntityId") — **33 files**: both
  bridge files, the JNI symbol allowlist, the ABI header, per-platform wrapper/interface files on
  both platforms, test mocks on both sides, and example/demo-app updates.
- `456e30d3` ("session: add mobile session configuration APIs") — **~50 files**, including new
  config classes on both platforms.

`capture-sdk/CLAUDE.md` states this plainly as policy, not just historical happenstance: every new
FFI surface requires updating the Rust export, the ABI header, and both platforms' wrapper layers
together, with focused bridge compilation/tests — Rust-only compilation is explicitly called out
as insufficient to validate this.

### §6. OTel/span/OTLP precedent

`bd-otlp-metrics` (`bd-otlp-metrics/src/{lib,delivery,http,offload,otlp,retry,metric}.rs`) is a
substantial, already-built OTLP exporter — for **metrics, not spans/traces** — that is currently
**not wired into the mobile SDK pipeline at all** (`grep -rl bd_otlp_metrics` across the whole
repo finds no consumer; capture-sdk doesn't depend on it). It includes:

- Vendored OTLP protobuf types, code-generated at build time (`bd-otlp-metrics/build.rs`) from
  real `opentelemetry-proto` files — but only `metrics`/`common`/`resource` protos, **no
  `trace.proto`/`trace_service.proto`**.
- A generic `HttpRemoteWriteClient` trait (`http.rs`) plus a `DeliveryEngine` (`delivery.rs`) that
  retries via the `backoff` crate with jitter.
- An `OffloadQueue` trait + `SerializedOffloadRequest` (`offload.rs`) that persists failed OTLP
  write requests (base64-encoded, compressed) for later retry.
- Snappy compression support and OTLP encode/decode helpers (`otlp.rs`).

This reads as infrastructure built for a different (likely server-side) use case. Its retry/offload
machinery turned out not to be needed for this feature (see "Plan at a glance" — spans are
time-sensitive, so there's no deferred delivery to build durable infrastructure for), but its
pattern for vendoring OTLP protobuf types via build-time codegen is exactly what the Path A payload
builder would reuse.

### §7. Size/footprint constraints

No explicit binary-size budget, numeric limit, or hard CI gate was found in either repo (checked
`shared-core`'s README/BENCHMARKS/deny.toml, and `capture-sdk`'s workflow YAML and shell scripts
for `threshold`/`MAX_`/"exceeds"/"fail" keywords — none found tied to size). Both platforms do
have PR-level size-delta *reporting* (not gating):

- iOS: `ci/capture_ios_binary_size.sh` measures the `ios-arm64` slice of `Capture.xcframework`;
  `.github/workflows/ios.yaml` posts a PR comment with the size delta.
- Android: `.github/workflows/android.yaml` measures the compressed `.so` inside the AAR plus APK
  size, diffing against a baseline stored on a dedicated `ci-baseline` branch.

The strongest **structural** (not documented-policy) evidence of a real size/footprint concern is
the architectural fact from §2: capture-sdk deliberately implements native
`PlatformNetworkManager`s per platform rather than shipping `hyper`+`hyper-rustls` in the mobile
binary. This plan treats that as the operative constraint even though no document states a number.

### §8. iOS network tracing today (capture-sdk)

- `platform/swift/source/integrations/url_session/TracePropagation.swift` —
  `URLSessionTracePropagationMode` enum (w3c/b3Single/b3Multi/datadog/disabled) and
  `URLSessionTraceContext` struct, generated via `SecRandomCopyBytes`.
- Header injection: `URLSessionTask+Swizzling.swift`, `URLSessionTask.cap_resume()` →
  `injectTraceHeadersIfNeeded()` — the swizzled replacement for `URLSessionTask.resume()`, which
  sets the trace header per mode and stashes the `URLSessionTraceContext` on the task via
  associated objects.
- Completion hook (Android `CaptureOkHttpEventListener` equivalent):
  `URLSessionTaskTracker.swift`, `task(_:didFinishCollecting:)`, called from
  `ProxyURLSessionDelegate` once `URLSessionTaskMetrics` are available — timing, status code, and
  the stashed trace context are all present simultaneously, same as Android. No existing OTel span
  code uses this hook point today; it currently only builds and logs `HTTPResponseInfo`.

### §9. Portability of today's Android OTel-export Kotlin code

Read in full: `OtelSpanModels.kt`, `OtelResourceAttributes.kt`, `OtelSpanBuilder.kt`,
`OtelSpanExporter.kt`, `OtelExportConfiguration.kt`, `TraceContextFactory.kt`.

- **Payload shape / attribute mapping**: OS-independent pure data transformation.
  `OtelSpanModels.kt`'s Gson-annotated wire types have zero Android-specific fields (only the
  input bundle, `HttpSpanExportData`, holds one `okhttp3.Request` field).
  `OtelSpanBuilder.build()` reads only from platform-neutral value objects
  (`HttpRequestInfo`/`HttpResponse`/`HttpRequestMetrics`/`TraceContext`) plus one direct field
  access (`data.httpRequest.url`, used just for `.toString()`/`.host`/`.port` — trivially
  replaceable with a neutral URL type). Protocol-string parsing and OK/ERROR status mapping are
  pure logic with no OS dependency.
- **Resource attributes**: mixed, as detailed in §4 — device ID and session ID already cross FFI;
  service/app/OS/device fields come from `ClientAttributes` (Android-OS-API-bound, but not new
  plumbing — the SDK already surfaces these as OOTB fields elsewhere); network/carrier/app-state
  are genuinely Android-only and need an iOS-specific (not shared) equivalent.
- **HTTP POST + auth header + serialization**: OkHttp-specific, not portable as written.
  `OtelSpanExporter.kt` is built entirely on `okhttp3.OkHttpClient`/`Call`/`Callback`, and its own
  doc comment states the current design intent plainly: *"best-effort... failures are reported to
  the error handler but never retried, since a dropped span must never affect the traced request
  itself"* — i.e., no persistence, no backoff, no offline queueing today. **Under the corrected
  offline-behavior design (see "Offline / device-health behavior"), this is not a bug — it's
  already the intended shape.** An earlier draft of this plan misread it as a gap; it isn't one.

### §10. Does today's ordinary HTTP log pipeline cross into Rust? (It does; span export deliberately doesn't.)

Yes for ordinary logs, confirmed by tracing the actual call chain:

- Android: `CaptureOkHttpEventListener.callEnd()`/`callFailed()` → `logger?.log(httpResponseInfo)`
  → `LoggerImpl.log(...)` → `logInternal(LogType.SPAN, ...)` → `CaptureJniLibrary.writeLog(...)`
  → `Java_io_bitdrift_capture_CaptureJniLibrary_writeLog` — straight into `bd-logger`'s
  offline-resilient ring buffer.
- iOS mirrors this: `Logger.log(_ request:)`/`log(_ response:)` → `LoggerBridge.log()` →
  `capture_write_log(...)` — the same Rust `bd-logger` path.

**But** the same `callEnd()`/`callFailed()` in `CaptureOkHttpEventListener.kt` *also* separately
calls `exportOtelSpanIfTraced(...)` → `IInternalLogger.exportOtelHttpSpan(data)` →
`LoggerImpl.exportOtelHttpSpan()`, which constructs the OTLP payload and calls
`OtelSpanExporter.export()` — pure Kotlin, OkHttp POST, **never touching JNI/Rust at all**. This is
correct, not a gap: `bd-logger`'s ring buffer exists precisely so that data which is useful no
matter when it's eventually delivered survives an outage; a span, which is only useful while its
trace is still assemblable, should not ride that same durable pipeline. The two paths being
different is the point.

### §11. Configuration plumbing precedent

No generic "Configuration" struct crosses the FFI boundary at logger-start time today. Verified
against two existing optional-feature configs on Android:

- **Session replay**: `LoggerImpl.kt` consumes `configuration.sessionReplayConfiguration` entirely
  in Kotlin, constructing a `SessionReplayTarget` (or a no-op if `null`); that *object* — not the
  raw config values — is what crosses FFI, as a `JObject` parameter of
  `Java_io_bitdrift_capture_CaptureJniLibrary_createLogger`. iOS mirrors this with `capture_create_logger`'s `session_replay_target: *mut Object` parameter.
- **Issue/crash reporting** follows the same shape: config is consumed Kotlin-side and passed as
  a callback object reference, not raw values.
- **OTel export config** (this feature) is *already exactly this pattern* today, and — since
  Rust never performs the send itself under either Path A or Path B — it never needs to reach Rust
  at all. Unlike the earlier draft of this plan, this is no longer a "new plumbing" concern.

### §12. iOS/Android size-tracking CI (same content as §7, iOS/Android specifics)

Covered fully in §7.

---

## Summary table

| Piece of the feature | Where it lives (Path B — no Rust) | Where it lives (Path A — Rust now) | Why |
|---|---|---|---|
| Trace ID / span ID generation | Per-platform (unchanged) | Per-platform (unchanged) | Cheap, already works, no shared logic to deduplicate, not worth new FFI |
| OS-specific attribute gathering (network type, carrier, app state, device info) | Per-platform (unchanged) | Per-platform (unchanged) | No Rust-reachable equivalent exists; these are OS APIs |
| OTLP span/resource payload construction | Per-platform (ported to both, kept in sync manually) | Rust, one stateless payload-building function | Pure data transformation, real duplication risk, real QA cost across two languages |
| Offline behavior | Per-platform: one export attempt, drop on any failure, no queue | Same rule, just enforced trivially since Rust holds no state either | A late span has no observability value — see "Offline / device-health behavior" |
| Actual HTTP POST | Per-platform (OkHttp/URLSession, unchanged either path) | Per-platform (unchanged) — Rust only returns bytes, never sends anything | Established precedent: never vendor a Rust HTTP/TLS client into the mobile binary |
| Export destination configuration | Per-platform (unchanged) | Per-platform (unchanged) — never needs to cross FFI, since Rust never sends the request | Only the platform ever performs the send, so only the platform needs to know the destination |
