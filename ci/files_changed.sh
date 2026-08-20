#!/bin/bash

# Checks whether the files in provided regex via the command line has changed when comparing the HEAD ref and
# $GITHUB_BASE_REF, i.e. the target branch (usually main). Writes a `changed`
# boolean to $GITHUB_OUTPUT; a non-zero exit indicates an actual error.
#
# Usage: ./ci/files_changed.sh <regex>
#        ./ci/files_changed.sh --files <path> [<path> ...]

set -euo pipefail

# Trap to handle unexpected errors and log them.
trap 'echo "An unexpected error occurred during file change check."; exit 1' ERR

# Determine the base ref or fallback to HEAD~1 when running on main
if [[ -z "${GITHUB_BASE_REF:-}" ]]; then
  echo "GITHUB_BASE_REF is empty, likely running on main branch. Using HEAD~1 for comparison."
  base_ref="HEAD~1"
else
  base_ref="origin/$GITHUB_BASE_REF"
fi

if git rev-parse --abbrev-ref HEAD | grep -q ^main$ ; then
  echo "Relevant file changes detected!"
  echo "changed=true" >> "$GITHUB_OUTPUT"
  exit 0
fi

# Compare committed PR changes only. Callers such as check_bazel.sh may generate files while
# computing their result; a one-argument git diff would include those working-tree side effects.
final_revision="${GITHUB_SHA:-HEAD}"
previous_revision=$(git merge-base "$base_ref" "$final_revision")
diff_output=$(git diff --name-only "$previous_revision" "$final_revision" || exit 1)

# Check for relevant file changes. Exact-file mode prevents callers from
# maintaining large, escape-heavy regular expressions.
if [[ "$1" == "--files" ]]; then
  shift
  if [[ $# -eq 0 ]]; then
    echo "--files requires at least one path."
    exit 2
  fi
  change_matches=$(printf '%s\n' "$diff_output" | grep -F -x -f <(printf '%s\n' "$@") || true)
else
  change_matches=$(printf '%s\n' "$diff_output" | grep -E "$1" || true)
fi

if [[ -n "$change_matches" ]]; then
  echo "$change_matches"
  echo "Relevant file changes detected!"
  echo "changed=true" >> "$GITHUB_OUTPUT"
  exit 0
else
  echo "No relevant changes found."
  echo "changed=false" >> "$GITHUB_OUTPUT"
fi
