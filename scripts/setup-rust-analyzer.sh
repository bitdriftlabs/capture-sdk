#!/usr/bin/env bash

set -euo pipefail

repository="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository"

tools="$repository/.rules_rust_analyzer"
selection_file="$tools/selection.json"
use_selection=true

usage() {
  cat <<'EOF'
Usage: ./scripts/setup-rust-analyzer.sh [--all]

Without arguments, an optional .rules_rust_analyzer/selection.json limits the
generated rust-project.json to selected local paths and their transitive Rust
dependencies. --all ignores that local selection for one invocation.
EOF
}

case "${1:-}" in
  "") ;;
  --all)
    use_selection=false
    shift
    ;;
  -h|--help)
    usage
    exit 0
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac

if (($#)); then
  usage >&2
  exit 2
fi

mkdir -p "$tools/bin" "$tools/libexec"
bazel="$repository/bazelw"

"$bazel" build \
  @rules_rs//tools/rust_analyzer:discover_bazel_rust_project \
  @rules_rs//tools/rust_analyzer:flycheck

execution_root="$("$bazel" info execution_root)"
output_base="$("$bazel" info output_base)"
discover_project="$("$bazel" cquery @rules_rs//tools/rust_analyzer:discover_bazel_rust_project --output=files)"
flycheck="$("$bazel" cquery @rules_rs//tools/rust_analyzer:flycheck --output=files | rg '/flycheck$')"
rust_analyzer="$("$bazel" cquery @rules_rust//rust/toolchain:current_rust_analyzer_toolchain --output=files | rg '/bin/rust-analyzer$')"
proc_macro_server="$("$bazel" cquery @rules_rust//rust/toolchain:current_rust_analyzer_toolchain --output=files | rg '/libexec/rust-analyzer-proc-macro-srv$')"
rustfmt="$("$bazel" cquery @rules_rust//rust/toolchain:current_rustfmt_toolchain --output=files | rg '/bin/rustfmt$')"

resolve_bazel_file() {
  local relative_path="$1"

  if [[ -e "$execution_root/$relative_path" ]]; then
    printf '%s\n' "$execution_root/$relative_path"
    return
  fi
  if [[ -e "$output_base/$relative_path" ]]; then
    printf '%s\n' "$output_base/$relative_path"
    return
  fi

  printf 'Bazel did not materialize %s\n' "$relative_path" >&2
  return 1
}

discover_project_path="$(resolve_bazel_file "$discover_project")"
flycheck_path="$(resolve_bazel_file "$flycheck")"
rust_analyzer_path="$(resolve_bazel_file "$rust_analyzer")"
proc_macro_server_path="$(resolve_bazel_file "$proc_macro_server")"
rustfmt_path="$(resolve_bazel_file "$rustfmt")"
# The execroot materializes only files used by the queried targets. Resolve the
# analyzer runtime from the output base, which contains its complete toolchain.
rustc_library_dir="$output_base/$(dirname "$(dirname "$proc_macro_server")")/lib"

if [[ ! -d "$rustc_library_dir" ]]; then
  printf 'Rust Analyzer runtime library directory is missing: %s\n' "$rustc_library_dir" >&2
  exit 1
fi

if [[ -e "$tools/lib" && ! -L "$tools/lib" ]]; then
  printf '%s already exists and is not an installer-managed symlink\n' "$tools/lib" >&2
  exit 1
fi
ln -sfn "$rustc_library_dir" "$tools/lib"

install -m 755 "$discover_project_path" "$tools/discover_bazel_rust_project.exe"
install -m 755 "$flycheck_path" "$tools/flycheck.exe"
install -m 755 "$rust_analyzer_path" "$tools/bin/rust-analyzer.exe"
install -m 755 "$proc_macro_server_path" "$tools/libexec/rust-analyzer-proc-macro-srv.exe"
install -m 755 "$rustfmt_path" "$tools/bin/rustfmt.exe"

settings_template="$repository/.vscode/settings.template.json"
settings_file="$repository/.vscode/settings.json"
generated_settings="$tools/vscode-settings.json"
if [[ -e "$settings_file" && ! -L "$settings_file" ]]; then
  printf '%s already exists and is not an installer-managed symlink\n' "$settings_file" >&2
  exit 1
fi
jq --arg rust_analyzer "$tools/bin/rust-analyzer.exe" \
  '."rust-analyzer.server.path" = $rust_analyzer' \
  "$settings_template" > "$generated_settings.tmp"
mv "$generated_settings.tmp" "$generated_settings"
ln -sfn "../.rules_rust_analyzer/vscode-settings.json" "$settings_file"

discovery_result="$(mktemp "$tools/discovery.XXXXXX")"
raw_project_tmp="$(mktemp "$tools/rust-project-raw.XXXXXX")"
project_tmp="$(mktemp "$tools/rust-project.XXXXXX")"
trap 'rm -f "$discovery_result" "$raw_project_tmp" "$project_tmp"' EXIT

# Nextest keeps the Rust compilation target behind its public shell test target,
# so it is tagged `manual`. Include manual targets during discovery: rules-rust
# then merges each real `rust_test` crate into its library crate, preserving the
# test configuration and dev-dependency graph for rust-analyzer.
if ! "$tools/discover_bazel_rust_project.exe" --workspace "$repository" --bazel_arg=--build_manual_tests | tail -n 1 > "$discovery_result"; then
  printf 'Bazel rust-project discovery failed\n' >&2
  exit 1
fi

if ! jq -e '.kind == "finished" and (.project | type == "object")' "$discovery_result" > /dev/null; then
  printf 'Bazel rust-project discovery did not return a project\n' >&2
  cat "$discovery_result" >&2
  exit 1
fi

jq '.project' "$discovery_result" > "$raw_project_tmp"

if "$use_selection" && [[ -f "$selection_file" ]]; then
  selection_paths="$(jq -ce '
    if type != "object" or (.paths | type) != "array" or (.paths | length) == 0 then
      error("expected a non-empty paths array")
    elif any(.paths[]; type != "string" or length == 0 or startswith("/") or (split("/") | index(".."))) then
      error("paths must be non-empty, relative paths without .. segments")
    else
      [.paths[] | sub("/+$"; "")]
    end
  ' "$selection_file")" || {
    printf '%s must be a JSON object with a non-empty relative paths array\n' "$selection_file" >&2
    exit 1
  }

  jq --arg workspace "$repository" --argjson paths "$selection_paths" '
    def dependency_closure($crates; $roots):
      reduce range(0; ($crates | length)) as $_ (
        ($roots | unique);
        . as $included
        | ($included + [
            $included[] as $crate_id
            | $crates[$crate_id].deps[]?.crate
          ] | unique)
      );

    . as $project
    | .crates as $crates
    | [
        range(0; ($crates | length))
        | select(
            ($crates[.].root_module // "") as $root_module
            | any($paths[]; . as $path
                | ($workspace + "/" + $path) as $prefix
                | $root_module == $prefix or ($root_module | startswith($prefix + "/")))
          )
      ] as $roots
    | if $roots == [] then
        error("selection did not match any local Rust crates")
      else
        .
      end
    | dependency_closure($crates; $roots) as $included
    | (reduce $included[] as $old_id (
        {crates: [], crate_ids: {}};
        .crate_ids[($old_id | tostring)] = (.crates | length)
        | .crates += [$crates[$old_id]]
      )) as $reindexed
    | $reindexed.crate_ids as $crate_ids
    | .crates = [
        $reindexed.crates[]
        | if has("deps") then
            .deps |= [
              .[]
              | .crate as $old_id
              | ($crate_ids[($old_id | tostring)]) as $new_id
              | select($new_id != null)
              | .crate = $new_id
            ]
          else
            .
          end
      ]
  ' "$raw_project_tmp" > "$project_tmp"

  printf 'Generated filtered rust-project.json with %s of %s crates from %s\n' \
    "$(jq '.crates | length' "$project_tmp")" \
    "$(jq '.crates | length' "$raw_project_tmp")" \
    "$selection_file"
else
  mv "$raw_project_tmp" "$project_tmp"
  printf 'Generated full rust-project.json with %s crates\n' "$(jq '.crates | length' "$project_tmp")"
fi

mv "$project_tmp" "$tools/rust-project.json"

root_project="$repository/rust-project.json"
if [[ -e "$root_project" && ! -L "$root_project" ]]; then
  printf '%s already exists and is not an installer-managed symlink\n' "$root_project" >&2
  exit 1
fi
ln -sfn ".rules_rust_analyzer/rust-project.json" "$root_project"

"$tools/bin/rust-analyzer.exe" --version
