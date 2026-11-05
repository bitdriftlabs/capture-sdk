# Backend artifacts and diagnostics

## Finding the attachment

A workflow-triggered command shows up in the session timeline as a `Command completed` log with
a `command_attachment` (`artifact_id`, `content_type`, `size_bytes`, `source: WORKFLOW`).
`commandId` is `null` for workflow-triggered commands, so look the session up instead:

- `run_cell.py` parses `sessionId=` from the hook's logcat line when there is an on-device copy.
- Otherwise `post_cell.py --workflow <id>` reads `bd workflow captured-sessions <id> --last 2h`
  and takes the newest session.
- `bd timeline logs <session> -ojson` returns the logs. Retry for up to ~4 minutes, because ingestion lags.

Link a session for humans with `bd timeline open <session> -ojson --jq .url -r`, and a workflow
with `bd workflow open <id> -ojson --jq .url -r`.

## Download routes

`scripts/fetch_backend.py` calls these with the bd CLI's own session JWT, the same way `bd` does:
`Authorization: Bearer <jwt>` to `https://api-cli.<domain>`. It never prints the token.

| route | returns |
|---|---|
| `GET /v1/workflow-commands/{artifact_id}/status` | `processing_state`, `partial`, `diagnostics`, availability flags |
| `GET /v1/workflow-commands/{artifact_id}?variant=raw` | the uploaded trace bytes (zstd content-encoding) |
| `GET /v1/workflow-commands/{artifact_id}?variant=processed` | a TAR: `trace.perfetto` (the raw bytes) + `symbols.pb` + `deobfuscation.pb` |
| `GET /v1/workflow-commands/{artifact_id}` | the default variant: processed when available |

trace_processor opens the TAR directly and applies both sidecars. A sidecar of exactly `0a 00`
(2 bytes) is empty: nothing was resolved for that sidecar.

Perfetto UI can't fetch these routes itself, because they need the auth header. Readers open a trace
by downloading it and dragging it into ui.perfetto.dev. Artifact pages can't host `.tar` files.

## Integrity checks (`post_cell.py`)

- `device_eq_raw`: SHA-256 of the on-device copy equals the raw download.
- `tar_trace_eq_raw`: `trace.perfetto` inside the TAR equals the raw download.
- `default_eq_processed`: the default download equals the processed one.

Any false here is a real bug. Rerun the cell once to rule out a mixed-up session before reporting it.

## Diagnostics glossary

Values come from `/status` → `diagnostics`. "Expected" describes what a healthy release trace on
this pipeline looks like. Anything else is a lead.

| diagnostic | meaning | expected |
|---|---|---|
| `partial` | backend marks the result incomplete | see the known behaviors below |
| `packet_loss_events` | packets with `previous_packet_dropped` | 0 for a complete trace |
| `heap_profile_packets`, `heap_graph_packets`, `usable_samples` | input volume | > 0 for the matching type |
| `native_addresses_requested` / `_resolved` | native frames sent to / named by the symbolizer | resolved = app-library frames |
| `native_missing_load_info` | frames in libraries without uploaded symbols (system libs) | > 0, harmless |
| `missing_build_ids` | mappings without a build ID | > 0 on Android, harmless |
| `java_methods_requested` / `_resolved` | obfuscated method frames looked up / found | equal |
| `java_methods_ambiguous` | resolved frames with more than one candidate | see the known behaviors |
| `java_jit_keys` | JIT-cache frames resolved through the app mapping | > 0 when JIT frames exist |
| `java_heap_classes_requested` / `_resolved`, `java_heap_fields_exported` | heap dump lookups | requested = resolved |
| `java_unresolved_packages` | classes the backend couldn't attribute to the app package | framework/boot classes |
| `java_process_recovery` | backend filled in the process name for traces that only have a pid | `recovered` for heap profiles; `already_identified` for stack sampling; `declined` on heap dumps (see below) |
| `partial_unwinds` | samples whose stack unwinding stopped early | small (a few); a large share means truncated stacks |
| `kernel_records_lost`, `invalid_mappings`, `invalid_stack_references`, `skipped_*`, `capture_time_inferred` | input-validation counters | 0 / false |

## Known behaviors to classify, not rediscover

Seen on staging in November 2026. Recheck each one instead of assuming it still holds. When it
does, report it as a gap with its suggestion.

- **`packet_loss_events` equals the number of data sequences (2 for heap traces, 3 for stack
  sampling), and so `partial: true`.** The walker (`walker.rs:223` in the monorepo backend) counts
  `previous_packet_dropped` on the first packet of each trusted sequence, which Perfetto sets by
  convention. `scripts/count_dropped_flags.py <raw trace>` confirms it per run:
  `flagged_only_on_first_packet: true` means no real loss. Suggestion: ignore it on the first packet
  seen per `trusted_packet_sequence_id` (or when `first_packet_on_sequence` is set).
  trace_processor reports no loss for the same traces.
- **`java_unresolved_packages` makes results `partial`** (`pipeline.rs:59`), although framework
  classes never have an app mapping. Suggestion: keep it as an informational count.
- **Method ambiguity.** For an obfuscated method, the backend returns every mapping entry joined
  with `|`, sorted alphabetically. That includes every method R8 inlined into it. A profiler frame
  is always the outer method, which R8 marks as the last line of each inline group (lines sharing
  one `a:b:` range). `verify_java_frames.py` computes how many frames would be unambiguous if only
  those were kept. trace_processor behaves identically, so this is not bitdrift-specific.
- **Sidecar size.** `deobfuscation.pb` holds every method of each referenced class, so for small
  profiles the processed download is often 10-30× the trace. `trace_processor bundle` packs the whole mapping
  (~40 MB), so ours is already smaller. Suggestion: emit only the referenced (class, method) pairs.
- **Native clone suffix format.** The backend writes `allocate.3572`, while `llvm-symbolizer` and
  trace_processor write `allocate (.3572)`. This is cosmetic.
- **`trace_processor_identity_noop` in the baseline comparison.** trace_processor applies the mapping to every
  class and frame, including framework classes that R8 lists under their own names, so it sets
  `deobfuscated_name` equal to the original name. The backend leaves non-app classes untouched. The
  display is identical, so this is not a finding.
- **`trace_processor bundle` prints a symbolization summary** listing system libraries without
  symbols, plus JIT and oat mappings "without build ID". That's expected noise: only the app's own
  libraries have uploaded debug files.
- **Main-thread samples have no thread name** in stack-sampling traces: `thread.name` is NULL where
  `tid == pid`. `analyze_frames.py` labels them `<main thread, unnamed>`. Expected, not a gap.
- **`java_process_recovery: declined (missing_sampled_pid)` on heap dumps.** Harmless, because heap
  dumps carry the process name. Suggestion: report `not_needed`.
