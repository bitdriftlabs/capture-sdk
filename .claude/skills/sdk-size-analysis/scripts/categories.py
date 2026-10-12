"""Ordered whole-function labels, not exact LTO instruction provenance."""

import re


GROUPS = {
    "Logger, configuration application, and upload orchestration":
        "bd_logger bd_log_primitives bd_log_util bd_events",
    "Workflow engine and compiled workflow support": "bd_workflows",
    "Log filters and matchers": "bd_log_filter bd_log_matcher",
    "API transport and stream management": "bd_api bd_grpc_codec",
    "Artifact upload and persistence": "bd_artifact_upload",
    "Log buffers and ring-buffer storage":
        "bd_buffer bd_event_buffer bd_bounded_buffer",
    "Metrics, statistics, and histograms":
        "bd_client_stats bd_client_stats_store bd_stats_common bd_histogram "
        "bd_sketches bd_workflow_stats sketches_rust ordered_float",
    "Runtime configuration": "bd_runtime",
    "SDK common utilities and state":
        "bd_client_common bd_session bd_state bd_key_value bd_versioned_kv "
        "bd_device bd_backoff bd_completion bd_shutdown bd_resource_utilization",
    "Crash and ANR processing":
        "bd_crash_handler bd_crash_reporter bd_crash bd_report_parsers",
    "Session replay": "bd_session_replay",
    "Android/JNI bridge": "capture_core capture_jni platform_shared jni jni_sys",
    "Regex and text search":
        "regex regex_lite regex_automata regex_syntax aho_corasick memchr",
    "Serde, JSON, and other data formats":
        "serde serde_core serde_json serde_yaml serde_yaml_ng base64 cesu8 combine nom",
    "Time and filesystem support":
        "bd_time time notify notify_types inotify memmap2 tempfile walkdir same_file",
    "FlatBuffers schemas, verification, and runtime": "flatbuffers",
    "Hashing, randomness, UUIDs, and crypto":
        "sha2 sha1 digest crypto_common rand rand_core rand_chacha getrandom uuid "
        "ahash chacha20 hybrid_array",
    "Compression and compression checksums":
        "miniz_oxide flate2 adler adler2 crc32fast zstd zstd_safe zstd_sys lz4_flex",
    "Async runtime, futures, and synchronization":
        "tokio tokio_util mio socket2 parking_lot parking_lot_core lock_api "
        "crossbeam_utils thread_local intrusive_collections signal_hook_registry",
    "Tracing and error handling":
        "bd_error_reporter bd_internal_logging tracing tracing_core "
        "tracing_subscriber log anyhow thiserror android_logger env_filter",
    "Rust standard library and generic support":
        "std core alloc hashbrown bytes smallvec once_cell rustc_demangle backtrace "
        "object addr2line gimli panic_unwind compiler_builtins",
}
CRATE_GROUPS = {
    crate: group for group, crates in GROUPS.items() for crate in crates.split()
}
PROTO_RULES = [
    ("Response decoder and configuration specializations", re.compile(
        r"bd_proto_util::serialization::inline::|::from_inline|::from_proto_bytes|"
        r"bd_log_matcher::matcher::inline::|bd_workflows::config::inline::|"
        r"bd_api::api::response::|bd_client_common::configuration::")),
    ("FlatBuffers schemas, verification, and runtime", re.compile(
        r"bd_proto::flatbuffers::|\bflatbuffers::")),
    ("Protobuf messages and type-specialized support", re.compile(r"bd_proto::protos::")),
    ("Protobuf wire/serialization runtime and helpers", re.compile(
        r"\bprotobuf::|bd_proto_util::serialization::|\bprotobuf_support::")),
]
PROTO_REFLECTION = re.compile(
    r"\bprotobuf::(?:reflect|descriptor)::|"
    r"\bbd_proto::[^\n]*(?:file_descriptor|generated_message_descriptor|::descriptor(?:$|::))"
)


def classify(name):
    if name.startswith("Java_") or name in {"JNI_OnLoad", "JNI_OnUnload"}:
        return "Android/JNI bridge", "JNI export"
    for category, pattern in PROTO_RULES:
        if pattern.search(name):
            return category, "proto/schema rule"
    if re.match(r"^<(?:str|char|bool|[ui](?:8|16|32|64|128|size)|f(?:32|64)|\[[^<>]+\])>", name):
        return CRATE_GROUPS["core"], "primitive Rust methods"
    if name.startswith("__rust_") or name == "rust_eh_personality":
        return CRATE_GROUPS["core"], "Rust runtime"
    references = list(dict.fromkeys(re.findall(r"\b([a-z][a-z0-9_]*)::", name)))
    sdk = next((crate for crate in references if crate.startswith("bd_") or
                crate in {"capture_core", "capture_jni", "platform_shared"}), None)
    if sdk:
        return CRATE_GROUPS.get(sdk, "Other SDK support"), sdk
    dependency = next((crate for crate in references if crate not in {"core", "alloc", "std"}
                       and (crate in CRATE_GROUPS or crate.startswith("futures_"))), None)
    if dependency:
        return (CRATE_GROUPS["tokio"] if dependency.startswith("futures_") else
                CRATE_GROUPS[dependency]), dependency
    root = references[0] if references else ""
    if root:
        return CRATE_GROUPS.get(root, "Other dependencies"), root
    if name.startswith("OUTLINED_FUNCTION_"):
        return "LLVM shared outlined functions", "LLVM"
    if re.search(r"(?:jemalloc|rjem_|je_|^malloc|^free$|^realloc|^calloc)", name):
        return "Native allocator", "allocator"
    if re.match(r"^(?:ZSTD_|HUF_|FSE_|XXH|LZ4_|mz_|tinfl_|tdefl_)", name):
        return CRATE_GROUPS["zstd"], "native compression"
    return "Other native runtime and support", "native/unnamed"
