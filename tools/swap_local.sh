#!/bin/bash

DEFAULT_PATH="../shared-core"
REPO_ROOT_DIR="$(dirname "$(dirname "$(realpath "$0")")")"
CARGO_TOML="$REPO_ROOT_DIR/Cargo.toml"

if [ ! -f "$CARGO_TOML" ]; then
  echo "Error: Cargo.toml not found in the root directory."
  exit 1
fi

if [ -d "$DEFAULT_PATH" ]; then
  CUSTOM_PATH=$DEFAULT_PATH
  echo "Using default path: $CUSTOM_PATH"
else
  if [ -z "$1" ]; then
    echo "Default path ($DEFAULT_PATH) not found."
    echo "Please provide a custom path to shared-core."
    echo "Usage: $0 <path_to_shared_core>"
    exit 1
  else
    CUSTOM_PATH=$1
  fi
fi

CUSTOM_PATH_ABS="$(realpath "$CUSTOM_PATH")"

echo "Using shared-core path: $CUSTOM_PATH_ABS"

# Run this to swap all of the deps to a local version for easy development.
clean_fields() {
  printf '%s' "$1" | /usr/bin/sed -E \
    -e 's/(^|,)[[:space:]]*git[[:space:]]*=[[:space:]]*"[^"]*"//g' \
    -e 's/(^|,)[[:space:]]*rev[[:space:]]*=[[:space:]]*"[^"]*"//g' \
    -e 's/(^|,)[[:space:]]*path[[:space:]]*=[[:space:]]*"[^"]*"//g' \
    -e 's/^[[:space:]]*,?[[:space:]]*//' \
    -e 's/[[:space:]]*,?[[:space:]]*$//'
}

write_dep_start() {
  local crate="$1"
  local extra="$2"

  if [ -n "$extra" ]; then
    printf '%s = { path = "%s/%s", %s\n' "$crate" "$CUSTOM_PATH_ABS" "$crate" "$extra"
  else
    printf '%s = { path = "%s/%s"\n' "$crate" "$CUSTOM_PATH_ABS" "$crate"
  fi
}

write_dep_single_line() {
  local crate="$1"
  local extra="$2"

  if [ -n "$extra" ]; then
    printf '%s = { path = "%s/%s", %s }\n' "$crate" "$CUSTOM_PATH_ABS" "$crate" "$extra"
  else
    printf '%s = { path = "%s/%s" }\n' "$crate" "$CUSTOM_PATH_ABS" "$crate"
  fi
}

TMP_FILE="$(mktemp)"
multiline_crate=""
dependency_fields=""
in_multiline_dependency=0

rewrite_dependency_entry() {
  local line="$1"

  if [[ ! "$line" =~ ^(bd-[A-Za-z0-9-]+)[[:space:]]*=[[:space:]]*\{(.*)$ ]]; then
    printf '%s\n' "$line" >> "$TMP_FILE"
    return
  fi

  multiline_crate="${BASH_REMATCH[1]}"
  dependency_fields="${BASH_REMATCH[2]}"

  if [[ "$line" == *"}"* ]]; then
    dependency_fields="${dependency_fields%%\}*}"
    write_dep_single_line "$multiline_crate" "$(clean_fields "$dependency_fields")" >> "$TMP_FILE"
    multiline_crate=""
    dependency_fields=""
    return
  fi

  write_dep_start "$multiline_crate" "$(clean_fields "$dependency_fields")" >> "$TMP_FILE"
  in_multiline_dependency=1
}

rewrite_dependency_continuation() {
  local line="$1"

  dependency_fields="$(clean_fields "$line")"

  if [[ "$line" == *"}"* ]]; then
    if [ -n "$dependency_fields" ]; then
      printf '%s }\n' "${dependency_fields%%\}*}" >> "$TMP_FILE"
    else
      printf '}\n' >> "$TMP_FILE"
    fi
    multiline_crate=""
    dependency_fields=""
    in_multiline_dependency=0
    return
  fi

  if [ -n "$dependency_fields" ]; then
    printf '%s\n' "$dependency_fields" >> "$TMP_FILE"
  fi
}

while IFS= read -r line; do
  if [ "$in_multiline_dependency" -eq 0 ]; then
    rewrite_dependency_entry "$line"
    continue
  fi

  rewrite_dependency_continuation "$line"
done < "$CARGO_TOML"

mv "$TMP_FILE" "$CARGO_TOML"
