set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

# Generate the Bazel-derived Rust Analyzer project and local editor configuration.
ra:
    @./scripts/setup-rust-analyzer.sh

# Select local paths for rust-analyzer and regenerate its filtered Bazel crate graph.
select-ra +paths:
    @selection_file=".rules_rust_analyzer/selection.json"; mkdir -p "$(dirname "$selection_file")"; selection_tmp="$(mktemp "$selection_file.XXXXXX")"; trap 'rm -f "$selection_tmp"' EXIT; jq -n --args '{paths: $ARGS.positional}' {{ paths }} > "$selection_tmp"; mv "$selection_tmp" "$selection_file"; ./scripts/setup-rust-analyzer.sh
